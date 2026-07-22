(ns refinery.actor
  "RefineryOperationsActor -- one refinery-operations coordination
  request = one supervised actor run, expressed as a REAL compiled
  `langgraph-clj` `StateGraph` (`langgraph.graph/state-graph` +
  `compile-graph`), per ADR-2607011000 / CLAUDE.md Actors section /
  skill `build-actor`. The Refinery Operations Advisor
  (`refinery.advisor`) is sealed into a single node (`:advise`); its
  proposal is ALWAYS routed through the independent Refinery
  Operations Governor (`:govern`, `refinery.governor/evaluate`,
  UNCHANGED) and the existing phase-gate state machine
  (`refinery.phase`, UNCHANGED) before anything ever reaches
  `refinery.store/log-action` (also UNCHANGED).

  This replaces the prior state of this namespace tree, where
  `deps.edn` declared `io.github.com-junkawasaki/langgraph-clj` as a
  real dependency and the README claimed a \"supervised superstep loop,
  interrupts\" -- but nothing in `src/` or `test/` ever required
  `langgraph.graph` or called `state-graph`/`add-node`/`compile-graph`.
  `refinery.phase` and `refinery.store/log-action` +
  `refinery.store/get-ledger` were likewise defined but never called
  from anywhere. This namespace is the real thing: it requires
  `langgraph.graph`, compiles a real graph, and is the only caller of
  `store/log-action` in this repository.

  State graph:
  ```text
  :intake -> :advise -> :govern -> :decide -+-> :commit             (clean, no violations)
                                             +-> :request-approval    (soft violation -> :commit)
                                             +-> :hold                (hard violation, permanent)
  ```

  Phase wiring (defense-in-depth, SECOND independent layer alongside
  the governor -- exactly as `refinery.phase`'s own docstring and the
  README already describe, now genuinely enforced instead of dead
  code): every transition below is validated through the EXISTING,
  UNCHANGED `refinery.phase/can-transition?` + `refinery.phase/
  phase-table`, and `:commit` refuses to run unless
  `refinery.phase/can-execute?` is true for the current phase --
  :idle -[:propose]-> :proposal -[:review]-> :review
    -[:approve]-> :execute -[:log]-> :logged   (commit path)
    -[:reject]->  :idle                        (hold path)

  The unconditional invariant (mirrors every cloud-itonami actor,
  `refinery.governor`'s own docstring, and `refinery.phase`'s \"only
  humans can approve the EXECUTE phase\" note): the
  RefineryOperationsAdvisor can never directly commit a record the
  RefineryOperationsGovernor refuses, and no proposal reaches the
  `:execute` phase without either (a) the governor finding it clean
  (no hard AND no soft violations -- routine, non-actuation logging/
  coordination only, since every real actuation op is a member of
  `refinery.governor/high-stakes` and therefore ALWAYS a soft
  violation) or (b) passing through the `:request-approval` interrupt,
  i.e. explicit human sign-off."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [refinery.advisor :as advisor]
            [refinery.governor :as governor]
            [refinery.phase :as phase]
            [refinery.store :as store]))

;; ----------------------------- phase transition helper -----------------------------

(defn- apply-transition
  "Advance `current` phase via `event`, using the EXISTING
  `refinery.phase/can-transition?` + `refinery.phase/phase-table`
  UNCHANGED. `refinery.phase/can-transition?`'s second argument is
  actually validated against the CURRENT phase's `:transitions` map
  KEYS (i.e. it is the transition *event*, e.g. `:propose`/`:review`/
  `:approve`/`:reject`/`:log` -- not the destination phase name,
  despite the parameter being named `next-phase` in that file, which
  this namespace does not modify). Throws if `event` is not a defined
  transition for `current` -- a genuine gate, exercised by
  `refinery.actor/build-graph`'s nodes and by
  `test/refinery/actor_test.cljc`, not decoration."
  [current event]
  (if (phase/can-transition? current event)
    (get-in phase/phase-table [current :transitions event])
    (throw (ex-info "Illegal refinery phase transition"
                    {:from current :event event}))))

;; ----------------------------- compiled StateGraph -----------------------------

(defn build-graph
  "Compiles a RefineryOperationsActor graph. opts:
    :advisor      -- a value accepted by `refinery.advisor/advise`
                      (default: `advisor/mock-advisor`)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                      (default: in-memory `cp/mem-checkpointer`)

  `refinery.store` is plain immutable EDN (no atom/protocol), so the
  store map is NOT captured at build time -- it flows through the
  graph as the `:store` channel, exactly like every other piece of
  request-scoped state. Callers pass the current store value (already
  seeded via `store/register-plant` / `store/register-equipment` /
  `store/verify-batch`) into `run-request!`; the graph's final `:store`
  channel value is the SAME store with the new ledger entry from
  `store/log-action` folded in -- the caller re-seats that returned
  value as the store for the next request, same pattern as any
  immutable Clojure accumulator."
  [& [{:keys [advisor checkpointer]
       :or   {advisor      (advisor/mock-advisor)
              checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :store       {:default nil}
         :phase       {:default :idle}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake
        (fn [{:keys [phase]}]
          (let [phase (or phase :idle)]
            (when-not (phase/can-propose? phase)
              (throw (ex-info "Refinery actor cannot accept a new proposal in this phase"
                              {:phase phase})))
            {:phase (apply-transition phase :propose)})))

      (g/add-node :advise
        (fn [{:keys [request phase]}]
          (let [p (advisor/advise advisor request)]
            {:proposal p
             :phase (apply-transition phase :review)
             :audit [{:node :advise :request request :proposal p}]})))

      (g/add-node :govern
        (fn [{:keys [proposal store]}]
          (let [v (governor/evaluate proposal store)]
            {:verdict v
             :audit [{:node :govern :verdict v}]})))

      (g/add-node :decide
        (fn [{:keys [proposal verdict phase]}]
          (cond
            ;; HARD violations are a permanent block -- never routed
            ;; through human approval, straight to :hold.
            (:holds? verdict)
            {:disposition :hold
             :phase (apply-transition phase :reject)
             :audit [{:node :decide :disposition :hold
                      :subject (:subject proposal)
                      :hard-violations (:hard-violations verdict)}]}

            ;; SOFT violations (low confidence OR a high-stakes
            ;; actuation op, per refinery.governor/high-stakes) always
            ;; escalate to a human via the :request-approval interrupt
            ;; -- the phase stays at :review until that human resumes.
            (seq (:soft-violations verdict))
            {:disposition :request-approval
             :audit [{:node :decide :disposition :request-approval
                      :subject (:subject proposal)
                      :soft-violations (:soft-violations verdict)}]}

            ;; Clean: no hard AND no soft violation -- routine,
            ;; non-actuation logging/coordination auto-commits.
            :else
            {:disposition :commit
             :phase (apply-transition phase :approve)
             :audit [{:node :decide :disposition :commit
                      :subject (:subject proposal)}]})))

      (g/add-node :request-approval
        (fn [{:keys [phase]}]
          ;; Resuming this interrupt-before-gated node IS the human
          ;; sign-off (same convention as every cloud-itonami sibling
          ;; actor) -- advance :review -> :execute now that a human
          ;; has approved, and flip :disposition from :request-approval
          ;; to :commit so the terminal state correctly reflects that
          ;; approval happened.
          {:disposition :commit
           :phase (apply-transition phase :approve)
           :audit [{:node :request-approval :approved? true}]}))

      (g/add-node :commit
        (fn [{:keys [request proposal store phase]}]
          (when-not (phase/can-execute? phase)
            (throw (ex-info "Refinery actor refused to commit outside the :execute phase"
                            {:phase phase})))
          (let [record {:op (:op proposal) :subject (:subject proposal) :proposal proposal}
                action {:type :commit
                        :op (:op proposal)
                        :subject (:subject proposal)
                        :request request
                        :proposal proposal
                        :phase-note (phase/phase-note phase)}
                store' (store/log-action store action)]
            {:record record
             :store store'
             :phase (apply-transition phase :log)
             :audit [{:node :commit :record record}]})))

      (g/add-node :hold
        (fn [{:keys [proposal verdict store]}]
          (let [action {:type :hold
                        :op (:op proposal)
                        :subject (:subject proposal)
                        :hard-violations (:hard-violations verdict)
                        :soft-violations (:soft-violations verdict)}
                store' (store/log-action store action)]
            {:store store'
             :audit [{:node :hold :action action}]})))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges
       :decide
       (fn [{:keys [disposition]}]
         (case disposition
           :commit :commit
           :request-approval :request-approval
           :hold)))

      (g/add-edge :request-approval :commit)
      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph {:checkpointer checkpointer
                         :interrupt-before #{:request-approval}})))

;; ----------------------------- run helpers -----------------------------

(defn run-request!
  "Run one refinery operation request to completion or interrupt.
  `store` is the current (already-seeded) `refinery.store` map.
  `thread-id` scopes checkpointing for resume after human approval.
  Returns the full run result: `{:state .. :events .. :status
  :done|:interrupted :frontier ..}`; `(:store (:state result))` is the
  new store value to carry forward (see `build-graph`'s docstring)."
  [graph store request thread-id]
  (g/run* graph {:request request :store store} {:thread-id thread-id}))

(defn approve!
  "Human-in-the-loop resume: the interrupted `:request-approval` node
  advances the phase to `:execute` and the graph proceeds straight to
  `:commit` (approval is the act of resuming the thread)."
  [graph thread-id]
  (g/run* graph nil {:thread-id thread-id :resume? true}))
