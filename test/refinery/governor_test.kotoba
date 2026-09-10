(ns refinery.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [refinery.advisor :as advisor]
            [refinery.governor :as governor]
            [refinery.store :as store]))

(deftest governor-evaluation
  (testing "Valid proposals pass governor"
    (let [st (store/init)
          st (store/register-plant st "plant-01" "Tokyo Refinery" "Tokyo")
          st (store/register-equipment st "eq-01" "plant-01" "Distillation A")
          st (store/verify-batch st "batch-01" "plant-01" "Gasoline" "CERT-001")
          advisor (advisor/mock-advisor)]

      (testing "process-reading proposal should pass"
        (let [proposal (advisor/log-process-reading-proposal advisor "plant-01" "Temperature" 280 "°C")
              result (governor/evaluate proposal st)]
          (is (not (:holds? result)))
          (is (empty? (:hard-violations result)))))

      (testing "maintenance proposal should escalate (soft)"
        (let [proposal (advisor/schedule-maintenance-proposal advisor "eq-01" "Inspection")
              result (governor/evaluate proposal st)]
          (is (not (:holds? result)))
          (is (seq (:soft-violations result)))))

      (testing "shipment proposal should pass"
        (let [proposal (advisor/coordinate-shipment-proposal advisor "batch-01" "Gasoline" 5000 "Osaka")
              result (governor/evaluate proposal st)]
          (is (not (:holds? result)))
          (is (empty? (:hard-violations result)))))))

  (testing "Process-control violations are HARD BLOCKS"
    (let [st (store/init)
          st (store/register-plant st "plant-01" "Tokyo Refinery" "Tokyo")
          proposal {:op :proposal/log-process-reading
                    :subject "plant-01"
                    :effect :propose
                    :cites ["Standard"]
                    :value {:detail "Adjust furnace temperature control valve"
                            :confidence 0.8}}
          result (governor/evaluate proposal st)]

      (is (:holds? result))
      (is (seq (:hard-violations result)))
      (is (= :process-control-forbidden (-> result :hard-violations first :rule)))))

  (testing "Unregistered plant is a HARD BLOCK"
    (let [st (store/init)
          advisor (advisor/mock-advisor)
          proposal (advisor/log-process-reading-proposal advisor "unknown-plant" "Temperature" 280 "°C")
          result (governor/evaluate proposal st)]

      (is (:holds? result))
      (is (seq (:hard-violations result)))
      (is (= :plant-not-registered (-> result :hard-violations first :rule)))))

  (testing "Emissions exceedance MUST escalate"
    (let [st (store/init)
          st (store/register-plant st "plant-01" "Tokyo Refinery" "Tokyo")
          advisor (advisor/mock-advisor)
          proposal (advisor/flag-emissions-exceedance-proposal advisor "report-01" "SO2" 120 100 true)
          result (governor/evaluate proposal st)]

      (is (:holds? result))
      (is (seq (:hard-violations result)))
      (is (= :emissions-threshold-exceedance (-> result :hard-violations first :rule)))))

  (testing "Emissions within limits pass"
    (let [st (store/init)
          st (store/register-plant st "plant-01" "Tokyo Refinery" "Tokyo")
          advisor (advisor/mock-advisor)
          proposal (advisor/flag-emissions-exceedance-proposal advisor "report-01" "SO2" 80 100 false)
          result (governor/evaluate proposal st)]

      (is (not (:holds? result)))
      (is (empty? (:hard-violations result)))))

  (testing "Unverified batch is a HARD BLOCK for shipment"
    (let [st (store/init)
          st (store/register-plant st "plant-01" "Tokyo Refinery" "Tokyo")
          advisor (advisor/mock-advisor)
          proposal (advisor/coordinate-shipment-proposal advisor "batch-unknown" "Gasoline" 5000 "Osaka")
          result (governor/evaluate proposal st)]

      (is (:holds? result))
      (is (seq (:hard-violations result)))
      (is (= :batch-not-verified (-> result :hard-violations first :rule))))))
