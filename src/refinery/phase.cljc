(ns refinery.phase
  "Refinery Operations Phase -- state machine phases for the refinery actor.

  Phases define which operations are allowed at each stage.
  The CRITICAL invariant: only human approvers can move to EXECUTE phase.

  Phase flow:
    IDLE -> PROPOSAL -> REVIEW -> EXECUTE -> LOGGED

  Only humans can approve the EXECUTE phase. The actor never auto-promotes
  a proposal to EXECUTE without explicit human sign-off.")

;; ----------------------------- phase definitions -----------------------------

(def phase-table
  "State machine phase definitions for refinery operations."
  {:idle
   {:label "Idle"
    :transitions {:propose :proposal}
    :can-propose? true
    :can-execute? false}

   :proposal
   {:label "Proposal"
    :transitions {:review :review :cancel :idle}
    :can-propose? false
    :can-execute? false}

   :review
   {:label "Review"
    :transitions {:approve :execute :reject :idle}
    :can-propose? false
    :can-execute? false
    :note "Human reviewer approves or rejects; only humans can move to EXECUTE"}

   :execute
   {:label "Execute"
    :transitions {:log :logged :error :review}
    :can-propose? false
    :can-execute? true
    :note "Real-world actuation phase; only humans initiate"}

   :logged
   {:label "Logged"
    :transitions {:propose :proposal}
    :can-propose? true
    :can-execute? false}})

;; ----------------------------- phase management -----------------------------

(defn valid-phase?
  "Check if a phase is defined in the phase table."
  [phase]
  (contains? phase-table phase))

(defn can-transition?
  "Check if a transition from current-phase to next-phase is allowed."
  [current-phase next-phase]
  (let [phase-def (phase-table current-phase)]
    (when phase-def
      (contains? (:transitions phase-def) next-phase))))

(defn can-propose?
  "Check if proposals can be made in this phase."
  [phase]
  (let [phase-def (phase-table phase)]
    (when phase-def (:can-propose? phase-def))))

(defn can-execute?
  "Check if real-world actuation can occur in this phase.
  This is a GATE: only humans can approve moving to EXECUTE."
  [phase]
  (let [phase-def (phase-table phase)]
    (when phase-def (:can-execute? phase-def))))

(defn phase-note
  "Get any note/comment for a phase."
  [phase]
  (let [phase-def (phase-table phase)]
    (when phase-def (:note phase-def))))
