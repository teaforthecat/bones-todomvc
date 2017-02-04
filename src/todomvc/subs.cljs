(ns todomvc.subs
  (:require [re-frame.core :refer [reg-sub subscribe]]))

(reg-sub
  :showing
  (fn [db _]
    (get-in db [:editable :todos :_meta :filter])))

(reg-sub
  :todos

  ;; signal function
  ;; returns a single input signal
  (fn [query-v _]
    (subscribe [:editable :todos]))

  ;; computation function
  ;; receives the input signal above as first parameter
  (fn [todos query-v _]
    (vals todos)))

(reg-sub
  :visible-todos

  ;; signal function
  ;; returns a vector of two input signals
  (fn [query-v _]
    [(subscribe [:todos])
     (subscribe [:showing])])

  ;; computation function
  ;; that 1st parameter is a 2-vector of values from above
  (fn [[todos showing] _]
    (let [filter-fn (case showing
                      :active (complement (comp :done :inputs))
                      :done   (comp :done :inputs)
                      :all    identity
                      nil identity)]
      (filter filter-fn todos))))

(reg-sub
  :all-complete?
  :<- [:todos]
  (fn [todos _]
    (seq todos)))

(reg-sub
  :completed-count
  :<- [:todos]
  (fn [todos _]
    (count (filter (comp :done :inputs) todos))))

(reg-sub
  :footer-counts
  :<- [:todos]
  :<- [:completed-count]
  (fn [[todos completed] _]
    [(- (count todos) completed) completed]))
