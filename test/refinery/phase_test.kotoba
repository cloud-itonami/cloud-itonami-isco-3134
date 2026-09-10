(ns refinery.phase-test
  "Direct unit tests for `refinery.phase` -- the state-machine phase
  table that `refinery.actor` now genuinely drives (previously this
  namespace had zero callers anywhere in the repository)."
  (:require [clojure.test :refer [deftest is testing]]
            [refinery.phase :as phase]))

(deftest valid-phase-table-membership
  (is (phase/valid-phase? :idle))
  (is (phase/valid-phase? :proposal))
  (is (phase/valid-phase? :review))
  (is (phase/valid-phase? :execute))
  (is (phase/valid-phase? :logged))
  (is (not (phase/valid-phase? :nonexistent))))

(deftest can-propose-only-in-idle-and-logged
  (is (true? (phase/can-propose? :idle)))
  (is (true? (phase/can-propose? :logged)))
  (is (false? (phase/can-propose? :proposal)))
  (is (false? (phase/can-propose? :review)))
  (is (false? (phase/can-propose? :execute))))

(deftest can-execute-only-in-execute
  (testing "the CRITICAL gate: only :execute allows real-world actuation"
    (is (true? (phase/can-execute? :execute)))
    (is (false? (phase/can-execute? :idle)))
    (is (false? (phase/can-execute? :proposal)))
    (is (false? (phase/can-execute? :review)))
    (is (false? (phase/can-execute? :logged)))))

(deftest full-happy-path-transitions
  (testing "idle -[propose]-> proposal -[review]-> review -[approve]-> execute -[log]-> logged"
    (is (phase/can-transition? :idle :propose))
    (is (= :proposal (get-in phase/phase-table [:idle :transitions :propose])))
    (is (phase/can-transition? :proposal :review))
    (is (= :review (get-in phase/phase-table [:proposal :transitions :review])))
    (is (phase/can-transition? :review :approve))
    (is (= :execute (get-in phase/phase-table [:review :transitions :approve])))
    (is (phase/can-transition? :execute :log))
    (is (= :logged (get-in phase/phase-table [:execute :transitions :log])))))

(deftest reject-path-returns-to-idle
  (is (phase/can-transition? :review :reject))
  (is (= :idle (get-in phase/phase-table [:review :transitions :reject]))))

(deftest undefined-transitions-are-rejected
  (testing "a phase can't skip straight to :execute without going through :review"
    (is (not (phase/can-transition? :idle :approve)))
    (is (not (phase/can-transition? :proposal :approve)))
    (is (not (phase/can-transition? :logged :approve)))))
