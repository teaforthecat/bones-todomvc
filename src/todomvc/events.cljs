(ns todomvc.events
  (:require
   [todomvc.db :as db :refer [default-value]]
   [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                          dispatch
                          after debug]]
   [bones.editable :as e]
   [bones.editable.local-storage :as e.ls]
   [bones.editable.request :as request]
   [bones.editable.response :as r]
   [bones.editable.helpers :as h]
   [bones.editable.protocols :as p]))

(reg-event-fx
  :initialise-db
  (fn [{:keys [db]} _]
    (let [client (e.ls/LocalStorage. "bones")]
      ;; this is the configuration of the bones.editable library
      (request/set-client client)
      ;; this will fetch the data and dispatch the :response/query handler
      (p/query client {:e-type "todos"} {})
      {:db default-value})))

(reg-event-fx
  :clear-completed
  [debug]
  (fn [{:keys [db]} _]
    (let [todos (get-in db [:editable :todos])
          ;; find the ids of all todos where :done is true
          ids (->> (vals todos)
                   (filter (comp :done :inputs))
                   (map (comp :id :inputs)))]
      (if (not-empty ids)
        ;; return db immediately, unchanged, the response handler will update
        ;; the dom (via data)
        {:dispatch [:request/command :todos/delete-many {:ids ids}]
         :db db}
        {:db db}))))

(reg-event-db
  :complete-all-toggle
  [(path :editable :todos)]
  (fn [todos _]
    (let [new-done (not-every? (comp :done :inputs)
                               (vals todos))]
      (reduce #(assoc-in %1 [%2 :inputs :done] new-done)
              todos
              (keys todos)))))


(defmethod r/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [
        ;; tap is optionally passed on from the caller
        ;; e-scope is generated and injected into tap by bones.editable
        {:keys [e-scope]} tap
        ;; the request data is returned as a command to update the dom
        {:keys [command args]} response
        [_ e-type identifier] e-scope
        {:keys [id]} args]

    ;; apply conventions
    (condp = command

      :todos/new
      {:dispatch (h/editable-response e-type identifier response)
       :db (assoc-in db [:editable e-type id :inputs] args)}

      :todos/update
      {:db (update-in db [:editable e-type id :inputs] merge args)
       :dispatch [:editable
                  [:editable e-type id :state :pending false]
                  [:editable e-type id :state :editing nil]]}

      :todos/delete
      {:db (update-in db [:editable e-type] dissoc id)}

      :todos/delete-many
      {:db (update-in db [:editable e-type] #(reduce dissoc % (:ids args)))})))

(defmethod r/handler [:response/query 200]
  [{:keys [db]} [channel response status tap]]
  {:db (update-in db [:editable :todos] merge (:results response))})
