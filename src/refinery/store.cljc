(ns refinery.store
  "Refinery Operations Store -- manages registered plants, batches, equipment, and audit ledger.")

;; ----------------------------- store initialization & accessors -----------------------------

(defn init
  "Initialize an empty store with registered entities."
  []
  {:plants {}
   :equipment {}
   :batches {}
   :audit-ledger []})

(defn register-plant
  "Register a refinery plant (identified by plant-id).
  A plant must be registered before any proposals can be made."
  [st plant-id name location]
  (assoc-in st [:plants plant-id]
            {:id plant-id
             :name name
             :location location
             :registered-at (java.time.Instant/now)}))

(defn plant-registered?
  "Check if a plant is registered in the store."
  [st plant-id]
  (contains? (:plants st) plant-id))

(defn register-equipment
  "Register a piece of refinery equipment."
  [st equipment-id plant-id equipment-type]
  (assoc-in st [:equipment equipment-id]
            {:id equipment-id
             :plant-id plant-id
             :type equipment-type
             :registered-at (java.time.Instant/now)}))

(defn equipment-registered?
  "Check if equipment is registered."
  [st equipment-id]
  (contains? (:equipment st) equipment-id))

(defn verify-batch
  "Mark a product batch as verified."
  [st batch-id plant-id product-type quality-cert]
  (assoc-in st [:batches batch-id]
            {:id batch-id
             :plant-id plant-id
             :product-type product-type
             :quality-cert quality-cert
             :verified? true
             :verified-at (java.time.Instant/now)}))

(defn batch-verified?
  "Check if a batch is verified."
  [st batch-id]
  (let [batch (get (:batches st) batch-id)]
    (and batch (:verified? batch))))

;; ----------------------------- audit ledger -----------------------------

(defn log-action
  "Record an action to the audit ledger."
  [st action-record]
  (update st :audit-ledger conj
          (assoc action-record :logged-at (java.time.Instant/now))))

(defn get-ledger
  "Retrieve the audit ledger."
  [st]
  (:audit-ledger st))
