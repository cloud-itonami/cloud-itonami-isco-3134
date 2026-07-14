(ns refinery.governor
  "Refinery Operations Governor -- the independent compliance layer that earns
  the Refinery Operations Advisor the right to propose and log actions.
  The LLM has no notion of refinery safety standards, emissions regulations,
  product quality requirements, or when a maintenance scheduling or shipment
  coordination is a real-world actuation, so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD.

  HARD violations (a human approver CANNOT override):
    1. Spec-basis       -- no official jurisdiction citation
    2. Plant/batch record verification -- no unregistered plant/batch
    3. Emissions threshold exceedance -- ALWAYS escalates (never silent log)
    4. Process-control operations -- NO valve control, flow control,
                                      emergency-shutdown decisions
                                      (those remain plant operator exclusive authority)

  SOFT violation (can be approved by human):
    5. Confidence floor / actuation gate -- low confidence OR real actuation

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates LOGISTICS, SCHEDULING, and COMPLIANCE PAPERWORK
  around the petroleum refining process. It does NOT:
    - Control furnaces, reactors, or process temperatures
    - Operate valves, flow control, or pressure regulation systems
    - Make emergency-shutdown decisions
    - Control product separation, distillation, or chemical composition
    - Operate processing equipment or monitoring hardware directly

  Those remain the exclusive authority of licensed refinery operators and engineers."
  (:require [refinery.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Operations that require human sign-off for real-world actuation:
  Maintenance scheduling and emissions reporting with exceedances."
  #{:actuation/schedule-maintenance :actuation/log-emissions-report})

(def process-control-keywords
  "Words that indicate process-control authority (FORBIDDEN for this actor).
  If a proposal mentions any of these, it's a hard block."
  #{"furnace-control" "reactor-control" "valve-control" "flow-control"
    "pressure-regulation" "temperature-setpoint" "distillation" "feed-rate"
    "shutdown" "emergency-stop" "process-parameters" "equipment-operation"
    "furnace" "reactor" "valve" "flow" "pressure" "setpoint"
    "parameters" "control-system" "scada" "charge" "discharge" "operator-control"
    "system-control" "actuate" "control-valve"})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's requirements."
  [proposal _st]
  (let [op (:op proposal)]
    (when (contains? #{:actuation/schedule-maintenance
                       :actuation/log-emissions-report} op)
      (when (or (empty? (:cites proposal))
                (and (contains? (:value proposal) :spec-basis)
                     (nil? (:spec-basis (:value proposal)))))
        [{:rule :no-spec-basis
          :detail "公式な仕様基準の引用が無い提案は処理できない"}]))))

(defn- plant-verification-violations
  "Plant record must be verified/registered before any action.
  This is a HARD violation -- no unregistered plants.
  NOTE: Only checks operations that directly reference a plant ID.
  Equipment operations check equipment, reports check themselves."
  [proposal st]
  (let [op (:op proposal)
        subject (:subject proposal)]
    (when (contains? #{:proposal/log-process-reading} op)
      (when-not (store/plant-registered? st subject)
        [{:rule :plant-not-registered
          :detail "製油所が登録されていない"}]))))

(defn- batch-verification-violations
  "Product batch record must be verified before shipment coordination.
  This is a HARD violation."
  [{:keys [op subject]} st]
  (when (= op :proposal/coordinate-shipment)
    (when-not (store/batch-verified? st subject)
      [{:rule :batch-not-verified
        :detail "製品バッチが未検証"}])))

(defn- process-control-block-violations
  "HARD BLOCK: This actor does NOT make process-control decisions.
  If a proposal mentions furnace control, valve control, temperature setpoints,
  flow control, shutdown, or other process parameters, reject it immediately.
  Those decisions remain the exclusive authority of licensed plant operators."
  [proposal _st]
  (let [detail (str (:detail (:value proposal)) " " (:op proposal))
        words (re-seq #"\w+" (.toLowerCase detail))
        forbidden (some #(contains? process-control-keywords %) words)]
    (when forbidden
      [{:rule :process-control-forbidden
        :detail (str "プロセス制御は認可オペレーターの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- emissions-threshold-exceedance-violations
  "If emissions report shows threshold exceedance, this MUST escalate to human.
  Never silently log a threshold exceedance."
  [{:keys [op]} {:keys [threshold-exceeded?]}]
  (when (and (= op :actuation/log-emissions-report) threshold-exceeded?)
    [{:rule :emissions-threshold-exceedance
      :detail "排出ガス基準超過は必ず人間にエスカレートされる"}]))

(defn- maintenance-equipment-verification-violations
  "Maintenance scheduling requires that equipment being serviced is registered."
  [{:keys [op subject]} st]
  (when (= op :actuation/schedule-maintenance)
    (when-not (store/equipment-registered? st subject)
      [{:rule :equipment-not-registered
        :detail "設備が未登録"}])))

(defn- confidence-gate-violations
  "Low confidence or high-stakes actuation -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (contains? high-stakes op))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "実際の操作には人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [spec-basis-violations
                           plant-verification-violations
                           batch-verification-violations
                           process-control-block-violations
                           maintenance-equipment-verification-violations]
        hard-checks-value [emissions-threshold-exceedance-violations]
        soft-checks [confidence-gate-violations]
        hard-violations-store (mapcat #(% proposal st) hard-checks-store)
        hard-violations-value (mapcat #(% proposal (:value proposal)) hard-checks-value)
        hard-violations (concat hard-violations-store hard-violations-value)
        soft-violations (mapcat #(% proposal (:value proposal)) soft-checks)]
    {:holds? (seq hard-violations)
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
