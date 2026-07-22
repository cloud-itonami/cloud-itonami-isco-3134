(ns refinery.actor-test
  "Integration tests for `refinery.actor/build-graph` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `refinery.actor/run-request!` / `refinery.actor/approve!` through the
  commit, escalate-then-approve, and hard-hold routes. These tests
  exist because none did before: `refinery.actor` did not exist, and
  `refinery.store/log-action` + `refinery.store/get-ledger` were dead
  code (zero callers). These tests prove the compiled graph is real,
  that the audit ledger is genuinely wired into the `:commit`/`:hold`
  nodes and stays empty until a real commit or hold happens, and that a
  governor HARD violation permanently blocks commit."
  (:require [clojure.test :refer [deftest is testing]]
            [refinery.actor :as actor]
            [refinery.store :as store]))

(deftest commit-path-clean-proposal
  (testing "a clean, non-actuation process-reading log commits through
            the real compiled graph and the ledger stays empty until
            that commit actually happens"
    (let [st (store/register-plant (store/init) "plant-01" "Tokyo Refinery" "Tokyo, Japan")]
      (is (empty? (store/get-ledger st)) "ledger starts empty")
      (let [graph (actor/build-graph)
            request {:op :proposal/log-process-reading
                     :plant-id "plant-01" :reading-type "Temperature"
                     :value 280.5 :unit "°C"}
            result (actor/run-request! graph st request "t-commit")
            state (:state result)]
        (is (= :done (:status result)))
        (is (= :commit (:disposition state)))
        (is (= :logged (:phase state)) "phase advanced through review -> execute -> logged")
        (is (some? (:record state)))
        (let [ledger (store/get-ledger (:store state))]
          (is (= 1 (count ledger)) "exactly one ledger entry -- the commit, nothing before it")
          (is (= :commit (:type (first ledger))))
          (is (= :proposal/log-process-reading (:op (first ledger))))
          (is (= "plant-01" (:subject (first ledger)))))))))

(deftest hold-path-unregistered-plant-is-a-hard-block
  (testing "an unregistered plant is a HARD governor violation -- the
            real graph routes straight to :hold (no interrupt, no
            human-approval detour), never reaches :commit, and the
            ledger records the hold (not a commit)"
    (let [st (store/init) ;; plant-01 deliberately NOT registered
          graph (actor/build-graph)
          request {:op :proposal/log-process-reading
                   :plant-id "plant-01" :reading-type "Temperature"
                   :value 280.5 :unit "°C"}
          result (actor/run-request! graph st request "t-hold")
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (is (nil? (:record state)) "governor rejection blocks the commit -- no record was ever produced")
      (let [ledger (store/get-ledger (:store state))]
        (is (= 1 (count ledger)))
        (is (= :hold (:type (first ledger))))
        (is (seq (:hard-violations (first ledger))))
        (is (= :plant-not-registered (-> ledger first :hard-violations first :rule)))))))

(deftest hold-path-process-control-forbidden-is-a-permanent-block
  (testing "a proposal mentioning process-control keywords is a HARD,
            permanent block -- never routed through human approval"
    (let [st (store/register-plant (store/init) "plant-01" "Tokyo Refinery" "Tokyo")
          graph (actor/build-graph)
          request {:op :proposal/log-process-reading
                   :plant-id "plant-01" :reading-type "furnace-control"
                   :value 300 :unit "°C"}
          result (actor/run-request! graph st request "t-hold-pc")
          state (:state result)]
      (is (= :hold (:disposition state)))
      (is (nil? (:record state)))
      (let [ledger (store/get-ledger (:store state))]
        (is (= :hold (:type (first ledger))))
        (is (= :process-control-forbidden (-> ledger first :hard-violations first :rule)))))))

(deftest escalate-then-approve-commits
  (testing "maintenance scheduling is a `refinery.governor/high-stakes`
            actuation op -- it ALWAYS escalates (soft violation), the
            real graph GENUINELY interrupts (checkpointed) at
            :request-approval, the ledger stays empty until a human
            approves, and `refinery.actor/approve!` resumes the SAME
            compiled graph through to :commit"
    (let [st (-> (store/init)
                 (store/register-plant "plant-01" "Tokyo Refinery" "Tokyo")
                 (store/register-equipment "eq-01" "plant-01" "Distillation Unit A"))
          graph (actor/build-graph)
          request {:op :actuation/schedule-maintenance
                   :equipment-id "eq-01" :maintenance-type "Routine Inspection"}
          held (actor/run-request! graph st request "t-escalate")]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/get-ledger (:store (:state held))))
          "hold-until-approved: nothing is logged while awaiting human sign-off")
      (let [approved (actor/approve! graph "t-escalate")
            state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition state)))
        (is (= :logged (:phase state)))
        (let [ledger (store/get-ledger (:store state))]
          (is (= 1 (count ledger)))
          (is (= :commit (:type (first ledger))))
          (is (= :actuation/schedule-maintenance (:op (first ledger))))
          (is (= "eq-01" (:subject (first ledger)))))))))
