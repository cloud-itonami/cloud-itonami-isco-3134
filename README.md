# cloud-itonami-isco-3134

Open Business Blueprint for **ISCO-08 3134**: petroleum and natural gas refining plant operators.

This repository publishes a community refinery operations coordinator -- process parameter logging, maintenance scheduling proposals, emissions compliance monitoring, and refined product shipment coordination -- as an OSS business that any qualified refinery operator can fork, deploy, run, improve and sell, so petroleum refineries can manage operations safely, with auditable decision records and hard safety gates preventing process-control operations from being delegated to an LLM, without renting a closed SaaS.

Built on this workspace's [`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj) StateGraph runtime (portable `.cljc`, supervised superstep loop, interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as every cloud-itonami actor (maintenance scheduling and emissions reporting with threshold exceedances are high-stakes operations requiring hard safety review and human sign-off before any real-world action).

## Scope: what this actor does and does not do

This actor covers routine process-parameter logging, maintenance scheduling proposals, emissions compliance monitoring, and refined product shipment coordination. It does **not** operate furnaces, control process parameters, regulate pressure/flow, or make emergency-shutdown decisions. Those remain the exclusive authority of licensed refinery operators and engineers.

### CRITICAL: What this actor does NOT do

- **NO furnace/reactor control** — remains operator/engineer exclusive authority
- **NO process-parameter control** (temperature, pressure, feed rate, etc.) — remains operator/engineer exclusive authority
- **NO valve control or flow regulation** — remains operator/engineer exclusive authority
- **NO emergency-shutdown decisions** — remains operator/engineer exclusive authority
- **NO equipment operation** — hardware operation remains operator exclusive; this actor only records readings and coordinates scheduling

Any proposal mentioning process control, valve operation, flow control, shutdown, or process parameters is immediately rejected as a hard violation.

### Actuation

**Dispatching a real maintenance scheduling or emissions-report logging is never autonomous, at any phase, by construction.** Two independent layers enforce this (`refinery.governor`'s high-stakes gates and `refinery.phase`'s phase table) -- see `refinery.phase`'s docstring and test suite. The actor may draft, check and recommend; a human refinery operator/engineer is always the one who actually schedules maintenance or logs emissions reports.

## The core contract

```
plant registration + equipment verification + batch verification
        |
        v
Refinery Operations Advisor -> Refinery Operations Governor -> logging proposal, maintenance proposal, shipment coordination, or human approval
        |
        v
robot actions (gated) + audit ledger + escalation to operator/engineer
```

No automated advice can schedule maintenance the governor refuses, verify a batch outside its registered scope, or publish an emissions report with threshold exceedance without governor approval and human sign-off. **A proposal mentioning process-control operations is permanently rejected, even with human approval.**

## The actor (real `langgraph-clj` `StateGraph`)

`refinery.actor/build-graph` compiles a real `langgraph.graph/state-graph`. One graph run = one operation request. `refinery.advisor`'s proposal is ALWAYS routed through `refinery.governor/evaluate` and `refinery.phase`'s state machine before anything reaches `refinery.store/log-action` (the append-only audit ledger):

```
:intake -> :advise -> :govern -> :decide -+-> :commit             (clean, no violations)
                                           +-> :request-approval    (soft violation -> :commit)
                                           +-> :hold                (hard violation, permanent)
```

`:request-approval` is an `interrupt-before` gate -- the compiled graph genuinely pauses (checkpointed) and waits for `refinery.actor/approve!` to resume it, so human sign-off is a real suspend/resume, not a flag. Every commit or hold reaches `refinery.store/log-action` exactly once, from `:commit`/`:hold` respectively -- the ledger stays empty until that happens.

```clojure
(require '[refinery.actor :as actor]
         '[refinery.store :as store])

(def st (-> (store/init)
            (store/register-plant "plant-01" "Tokyo Refinery" "Tokyo, Japan")))

(def graph (actor/build-graph))

;; clean, routine proposal -> commits immediately
(actor/run-request! graph st
  {:op :proposal/log-process-reading :plant-id "plant-01"
   :reading-type "Temperature" :value 280.5 :unit "°C"}
  "thread-1")

;; a high-stakes actuation op ALWAYS escalates -- interrupts at :request-approval
(def held (actor/run-request! graph st
            {:op :actuation/schedule-maintenance :equipment-id "eq-01"
             :maintenance-type "Routine Inspection"}
            "thread-2"))
;; => {:status :interrupted, :frontier [:request-approval], ...}

;; human sign-off resumes the SAME thread through to :commit
(actor/approve! graph "thread-2")
```

## Proposal Operations

- `:log-process-reading` — routine parameter reading logging (temperature, pressure, etc.)
- `:schedule-maintenance` — maintenance scheduling proposal (inspection, service, replacement)
- `:flag-emissions-exceedance` — emissions threshold exceedance (ALWAYS escalates, never silent)
- `:coordinate-shipment` — refined-product shipment coordination

## Safety Invariants (HARD blocks)

1. **Plant/batch verification** — all operations require registered plant/batch records
2. **Spec-basis citation** — all operations require official regulation citations
3. **Emissions escalation** — threshold exceedances MUST escalate to human (never silent log)
4. **Process-control prohibition** — any proposal mentioning process control is permanently blocked
5. **Confidence threshold** — low-confidence or high-stakes proposals escalate to human

## Building & Testing

```bash
# Run tests (governor, phase, store, and the compiled actor StateGraph end-to-end)
clojure -M:test

# Run the governor/phase demo (refinery.sim)
clojure -M:dev:run

# Lint
clojure -M:lint
```

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation) (ISCO-08 `3134`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs

## License

AGPL-3.0-or-later.
