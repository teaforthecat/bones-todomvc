(ns todomvc.events
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

;; Event handlers change state, that's their job. But what happens if there's
;; a bug which corrupts app state in some subtle way? This interceptor is run after
;; each event handler has finished, and it checks app-db against a spec.  This
;; helps us detect event handler bugs early.
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



;; -- Client --
(defrecord TestClient []
  e/Client
  (login   [client args tap]
    (dispatch [:response/login {} 200 tap]))
  (logout  [client tap]
    (dispatch [:response/logout {} 200 tap]))
  (command [client cmd args tap]
    (condp = cmd
      :delete-todo
      (dispatch [:response/command {:args args :command cmd} 200 tap])
      :todos
      (dispatch [:response/command {:args (merge (:defaults tap) ;;TODO: on new only, maybe
                                                 {:id (random-uuid)}
                                                 args ;; will override :id
                                                 )
                                    :command cmd} 200 tap])))
  (query   [client args tap]
    (dispatch [:response/query {} 200 tap])))


;; -- Event Handlers ----------------------------------------------------------

;; usage:  (dispatch [:initialise-db])
(reg-event-fx                     ;; on app startup, create initial state
  :initialise-db                  ;; event id being handled
  [(inject-cofx :local-store-todos)  ;; obtain todos from localstore
   check-spec-interceptor]                                  ;; after the event handler runs, check that app-db matches the spec
  (fn [{:keys [db local-store-todos]} _]                    ;; the handler being registered

    ;; TODO: generate responses
    (let [client (TestClient.)]
      (e/set-client client)
      {:db (assoc default-value
                  :todos local-store-todos)})))  ;; all hail the new state


;; usage:  (dispatch [:set-showing  :active])
(reg-event-db                     ;; this handler changes the todo filter
  :set-showing                    ;; event-id

  ;; this chain of two interceptors wrap the handler
  [check-spec-interceptor (path :showing) trim-v]

  ;; The event handler
  ;; Because of the path interceptor above, the 1st parameter to
  ;; the handler below won't be the entire 'db', and instead will
  ;; be the value at a certain path within db, namely :showing.
  ;; Also, the use of the 'trim-v' interceptor means we can omit
  ;; the leading underscore from the 2nd parameter (event vector).
  (fn [old-keyword [new-filter-kw]]  ;; handler
    new-filter-kw))                  ;; return new state for the path


;; usage:  (dispatch [:add-todo  "Finish comments"])
(reg-event-db                     ;; given the text, create a new todo
  :add-todo

  ;; The standard set of interceptors, defined above, which we
  ;; apply to all todos-modifiing event handlers. Looks after
  ;; writing todos to local store, etc.
  todo-interceptors

  ;; The event handler function.
  ;; The "path" interceptor in `todo-interceptors` means 1st parameter is :todos
  (fn [todos [text]]
    (let [id (allocate-next-id todos)]
      (assoc todos id {:id id :title text :done false}))))


(reg-event-db
  :toggle-done
  todo-interceptors
  (fn [todos [id]]
    (update-in todos [id :done] not)))


(reg-event-db
  :save
  todo-interceptors
  (fn [todos [id title]]
    (assoc-in todos [id :title] title)))


(reg-event-db
  :delete-todo
  todo-interceptors
  (fn [todos [id]]
    (dissoc todos id)))


(reg-event-db
  :clear-completed
  todo-interceptors
  (fn [todos _]
    (->> (vals todos)                ;; find the ids of all todos where :done is true
         (filter :done)
         (map :id)
         (reduce dissoc todos))))    ;; now delete these ids


(reg-event-db
  :complete-all-toggle
  todo-interceptors
  (fn [todos _]
    (let [new-done (not-every? :done (vals todos))]   ;; work out: toggle true or false?
      (reduce #(assoc-in %1 [%2 :done] new-done)
              todos
              (keys todos)))))
