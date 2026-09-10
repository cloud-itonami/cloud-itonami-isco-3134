(ns refinery.sim
  "Refinery Operations Simulation -- demo driver for testing the actor.

  Usage:
    clojure -M:dev:run"
  (:require [refinery.advisor :as advisor]
            [refinery.governor :as governor]
            [refinery.phase :as phase]
            [refinery.store :as store]))

(defn -main
  "Run a simple demo of the refinery actor."
  [& _args]
  (println "\n=== Refinery Operations Coordination Actor Demo ===\n")

  ;; Initialize store
  (let [st (store/init)
        ;; Register a plant
        st (store/register-plant st "plant-01" "Tokyo Refinery" "Tokyo, Japan")
        ;; Register equipment
        st (store/register-equipment st "eq-01" "plant-01" "Distillation Unit A")
        ;; Verify a batch
        st (store/verify-batch st "batch-001" "plant-01" "Premium Gasoline" "CERT-2026-001")
        advisor (advisor/mock-advisor)]

    ;; Demo 1: Valid process-reading proposal
    (println "Demo 1: Valid process-reading proposal")
    (let [proposal (advisor/log-process-reading-proposal advisor "plant-01" "Temperature" 280.5 "°C")]
      (println "  Proposal:" proposal)
      (let [result (governor/evaluate proposal st)]
        (println "  Governor result:" (dissoc result :hard-violations :soft-violations))
        (if (:clean? result)
          (println "  ✓ APPROVED")
          (println "  ✗ REJECTED")))
      (println))

    ;; Demo 2: Maintenance scheduling proposal
    (println "Demo 2: Maintenance scheduling proposal")
    (let [proposal (advisor/schedule-maintenance-proposal advisor "eq-01" "Routine Inspection")]
      (println "  Proposal:" proposal)
      (let [result (governor/evaluate proposal st)]
        (println "  Governor result:" (dissoc result :hard-violations :soft-violations))
        (if (seq (:soft-violations result))
          (println "  ⚠ SOFT VIOLATIONS (escalate to human):" (map :rule (:soft-violations result)))
          (println "  ✓ NO SOFT VIOLATIONS")))
      (println))

    ;; Demo 3: Process-control violation (HARD BLOCK)
    (println "Demo 3: Process-control violation (HARD BLOCK)")
    (let [proposal {:op :proposal/log-process-reading
                    :subject "plant-01"
                    :effect :propose
                    :cites ["Standard"]
                    :value {:detail "Adjust furnace temperature setpoint to 300°C"
                            :confidence 0.8}}]
      (println "  Proposal:" proposal)
      (let [result (governor/evaluate proposal st)]
        (println "  Governor result:" (dissoc result :soft-violations))
        (if (seq (:hard-violations result))
          (println "  ✗ HARD VIOLATIONS (permanently blocked):" (map :rule (:hard-violations result)))
          (println "  ✓ NO HARD VIOLATIONS")))
      (println))

    ;; Demo 4: Emissions exceedance (requires escalation)
    (println "Demo 4: Emissions exceedance (requires escalation)")
    (let [proposal (advisor/flag-emissions-exceedance-proposal advisor "report-01" "SO2" 120 100 true)]
      (println "  Proposal:" proposal)
      (let [result (governor/evaluate proposal st)]
        (println "  Governor result:" (dissoc result :soft-violations))
        (if (seq (:hard-violations result))
          (println "  ✗ HARD VIOLATIONS (escalate to human):" (map :rule (:hard-violations result)))
          (println "  ✓ NO HARD VIOLATIONS")))
      (println))

    ;; Demo 5: Shipment coordination proposal
    (println "Demo 5: Shipment coordination proposal")
    (let [proposal (advisor/coordinate-shipment-proposal advisor "batch-001" "Premium Gasoline" 5000 "Osaka")]
      (println "  Proposal:" proposal)
      (let [result (governor/evaluate proposal st)]
        (println "  Governor result:" (dissoc result :hard-violations :soft-violations))
        (if (:clean? result)
          (println "  ✓ APPROVED")
          (println "  ✗ REJECTED")))
      (println))

    ;; Demo 6: Unregistered plant (HARD BLOCK)
    (println "Demo 6: Unregistered plant (HARD BLOCK)")
    (let [proposal (advisor/log-process-reading-proposal advisor "plant-unknown" "Temperature" 280.5 "°C")]
      (println "  Proposal:" proposal)
      (let [result (governor/evaluate proposal st)]
        (println "  Governor result:" (dissoc result :soft-violations))
        (if (seq (:hard-violations result))
          (println "  ✗ HARD VIOLATIONS (plant not registered):" (map :rule (:hard-violations result)))
          (println "  ✓ NO HARD VIOLATIONS")))
      (println)))

  (println "=== Demo complete ===\n"))
