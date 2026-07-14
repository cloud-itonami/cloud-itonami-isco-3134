(ns refinery.advisor
  "Refinery Operations Advisor -- the LLM-driven suggestion layer.
  Proposes operations to the Governor for approval.")

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

(defn log-process-reading-proposal
  "Propose a routine process-parameter reading log."
  [_advisor plant-id reading-type value unit]
  {:op :proposal/log-process-reading
   :subject plant-id
   :effect :propose
   :cites ["Petroleum Industry Safety and Health Regulation (石油産業安全衛生規則) §15"]
   :value {:evidence {:instrument-calibrated true :measurement-date true}
           :confidence 0.85
           :reading-type reading-type
           :value value
           :unit unit
           :detail (str "Routine process parameter logging: " reading-type " = " value " " unit)}})

(defn schedule-maintenance-proposal
  "Propose a maintenance schedule for refinery equipment."
  [_advisor equipment-id maintenance-type]
  {:op :actuation/schedule-maintenance
   :subject equipment-id
   :effect :propose
   :cites ["Petroleum Industry Safety and Health Regulation (石油産業安全衛生規則) §22"]
   :value {:evidence {:equipment-inspection true :maintenance-history true :safety-plan true}
           :confidence 0.88
           :maintenance-type maintenance-type
           :detail (str "Equipment maintenance scheduling: " maintenance-type " for " equipment-id)}})

(defn flag-emissions-exceedance-proposal
  "Propose an emissions report (may include threshold exceedances).
  This ALWAYS requires escalation if threshold is exceeded."
  [_advisor report-id emissions-type value threshold threshold-exceeded?]
  {:op :actuation/log-emissions-report
   :subject report-id
   :effect :propose
   :cites ["Air Pollution Control Law (大気汚染防止法) §3"]
   :value {:evidence {:monitoring-data true :calibration-cert true}
           :confidence 0.92
           :emissions-type emissions-type
           :value value
           :threshold threshold
           :threshold-exceeded? threshold-exceeded?
           :detail (if threshold-exceeded?
                    (str "Emissions monitoring: " emissions-type " (" value ") exceeds threshold ("
                         threshold "), escalation required")
                    (str "Emissions within compliance range: " emissions-type " (" value ")"))}})

(defn coordinate-shipment-proposal
  "Propose a refined-product shipment coordination."
  [_advisor batch-id product-type quantity destination]
  {:op :proposal/coordinate-shipment
   :subject batch-id
   :effect :propose
   :cites ["Petroleum Products Quality Regulation (石油製品品質規則) §8"]
   :value {:evidence {:batch-verified true :quality-cert true :transport-plan true}
           :confidence 0.87
           :product-type product-type
           :quantity quantity
           :destination destination
           :detail (str "Product shipment coordination: " quantity " units of " product-type
                       " to " destination)}})
