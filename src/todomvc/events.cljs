(ns todomvc.events
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [todomvc.db :as db :refer [default-value todos->local-store]]
   [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                          dispatch
                          after debug]]
   [bones.editable :as e]
   [bones.editable.local-storage :as e.ls]
   [bones.editable.request :as request]
   [bones.editable.protocols :as p]
   [cljs.spec     :as s]))

(defn check-and-throw
  "throw an exception if db doesn't match the spec"
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

(def check-spec-interceptor (after (partial check-and-throw :todomvc.db/db)))

(reg-event-fx
  :initialise-db
  [check-spec-interceptor]
  (fn [{:keys [db local-store-todos]} _]
    (let [client (e.ls/LocalStorage. "bones")]
      ;; this is the configuration of the bones.editable library
      (request/set-client client)
      (p/query client {:form-type "todos"} {})
      {:db default-value}
      )))  ;; all hail the new state

(reg-event-fx
  :clear-completed
  [debug]
  (fn [{:keys [db]} _]
    (let [;; find the ids of all todos where :done is true
          todos (get-in db [:editable :todos])
          ids (->> (vals todos)
                   (filter (comp :done :inputs))
                   (map (comp :id :inputs))
                   )]
      (if (not-empty ids)
        ;; return db immediately, unchanged
        {:dispatch [:request/command :todos/delete-many {:ids ids}]
         :db db}
        {:db db}))))

(reg-event-db
  :complete-all-toggle
  [check-spec-interceptor (path :editable :todos) trim-v]
  (fn [todos _]
    (let [new-done (not-every? (comp :done :inputs) (vals todos))]   ;; work out: toggle true or false?
      (reduce #(assoc-in %1 [%2 :inputs :done] new-done)
              todos
              (keys todos)))))
