(ns todomvc.events
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [todomvc.db :as db :refer [default-value todos->local-store]]
   [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                          dispatch
                          after debug]]
   [bones.editable :as e]
   [cljs.spec     :as s]))


;; -- Interceptors --------------------------------------------------------------
;;

(defn check-and-throw
  "throw an exception if db doesn't match the spec"
  [a-spec db]
  (when-not (s/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (s/explain-str a-spec db)) {}))))

(def check-spec-interceptor (after (partial check-and-throw :todomvc.db/db)))

;; this interceptor stores todos into local storage
;; we attach it to each event handler which could update todos
(def ->local-store (after todos->local-store))

;; Each event handler can have its own set of interceptors (middleware)
;; But we use the same set of interceptors for all event habdlers related
;; to manipulating todos.
;; A chain of interceptors is a vector.
(def todo-interceptors [check-spec-interceptor               ;; ensure the spec is still valid
                        (path :todos)                        ;; 1st param to handler will be the value from this path
                        ->local-store                        ;; write todos to localstore
                        (when ^boolean js/goog.DEBUG debug)  ;; look in your browser console for debug logs
                        trim-v])                             ;; removes first (event id) element from the event vec


;; -- Helpers -----------------------------------------------------------------

(defn allocate-next-id
  "Returns the next todo id.
  Assumes todos are sorted.
  Returns one more than the current largest id."
  [todos]
  ((fnil inc 0) (last (keys todos))))



;; -- Event Handlers ----------------------------------------------------------

;; usage:  (dispatch [:initialise-db])
(reg-event-fx                     ;; on app startup, create initial state
  :initialise-db                  ;; event id being handled
  [(inject-cofx :local-store-todos)  ;; obtain todos from localstore
   check-spec-interceptor]                                  ;; after the event handler runs, check that app-db matches the spec
  (fn [{:keys [db local-store-todos]} _]                    ;; the handler being registered

    (let [client (e/LocalStorage. "bones")]
      ;; this is the configuration of the bones.editable library
      (e/set-client client)
      (e/query client {:form-type "todos"} {})
      {:db default-value}
      )))  ;; all hail the new state

(defmethod e/handler [:response/query 200]
  [{:keys [db]} [channel response status tap]]
  {:db (update-in db [:editable :todos] merge (:results response))})

;; usage:  (dispatch [:set-showing  :active])
(reg-event-db                     ;; this handler changes the todo filter
  :set-showing                    ;; event-id

  ;; this chain of two interceptors wrap the handler
  [check-spec-interceptor (path :editable :todos :_meta :filter) trim-v]
  (fn [old-keyword [new-filter-kw]]  ;; handler
    new-filter-kw))                  ;; return new state for the path


(reg-event-fx
  :clear-completed
  [check-spec-interceptor debug]
  (fn [db _]
    (let [;; find the ids of all todos where :done is true
          todos (get-in db [:editable :todos])
          ids (->> (vals todos)
                   (filter (comp :done :inputs))
                   (map (comp :id :inputs))
                   )]
      (println "ids")
      (println ids)
      (if (first ids)
        ;; return db immediately, unchanged
        {:dispatch [:request/command :todos (first ids) {:command :todos/delete}]
         :db db}
        {:db db}))))


(comment

  (first
   (->> (vals (get-in @re-frame.db/app-db [:editable :todos]) )
        (filter (comp :done :inputs))
        (map (comp :id :inputs))
        ))

  )

(reg-event-db
  :complete-all-toggle
  [check-spec-interceptor (path :editable :todos) trim-v]
  (fn [todos _]
    (let [new-done (not-every? (comp :done :inputs) (vals todos))]   ;; work out: toggle true or false?
      (reduce #(assoc-in %1 [%2 :inputs :done] new-done)
              todos
              (keys todos)))))
