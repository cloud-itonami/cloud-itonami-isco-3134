(ns refinery.store-test
  "Direct unit tests for `refinery.store` -- in particular `log-action`/
  `get-ledger`, which had ZERO callers anywhere in this repository
  before `refinery.actor` was built. These tests are independent of
  the StateGraph so a regression in the store itself is falsifiable
  without going through the whole graph."
  (:require [clojure.test :refer [deftest is testing]]
            [refinery.store :as store]))

(deftest init-store-is-empty
  (testing "a freshly initialized store has no registrations and an empty ledger"
    (let [st (store/init)]
      (is (empty? (:audit-ledger st)))
      (is (empty? (store/get-ledger st)))
      (is (not (store/plant-registered? st "plant-01")))
      (is (not (store/equipment-registered? st "eq-01")))
      (is (not (store/batch-verified? st "batch-01"))))))

(deftest register-plant-makes-it-registered
  (testing "register-plant is reflected by plant-registered?, other plants stay unregistered"
    (let [st (store/register-plant (store/init) "plant-01" "Tokyo Refinery" "Tokyo")]
      (is (store/plant-registered? st "plant-01"))
      (is (not (store/plant-registered? st "plant-02"))))))

(deftest register-equipment-makes-it-registered
  (let [st (store/register-equipment (store/init) "eq-01" "plant-01" "Distillation Unit A")]
    (is (store/equipment-registered? st "eq-01"))
    (is (not (store/equipment-registered? st "eq-02")))))

(deftest verify-batch-makes-it-verified
  (let [st (store/verify-batch (store/init) "batch-01" "plant-01" "Premium Gasoline" "CERT-2026-001")]
    (is (store/batch-verified? st "batch-01"))
    (is (not (store/batch-verified? st "batch-02")))))

(deftest log-action-is-append-only-and-preserves-order
  (testing "log-action conj-appends to the ledger without mutating the input store
            (refinery.store is plain immutable EDN, not an atom)"
    (let [st0 (store/init)
          st1 (store/log-action st0 {:type :commit :op :proposal/log-process-reading :subject "plant-01"})
          st2 (store/log-action st1 {:type :hold :op :actuation/schedule-maintenance :subject "eq-01"})]
      (is (empty? (store/get-ledger st0)) "the original store value is untouched")
      (is (= 1 (count (store/get-ledger st1))))
      (is (= 2 (count (store/get-ledger st2))))
      (is (= [:commit :hold] (map :type (store/get-ledger st2))) "append order is preserved")
      (is (every? :logged-at (store/get-ledger st2)) "log-action stamps every entry"))))
