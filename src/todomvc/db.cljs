(ns todomvc.db
  (:require [cljs.reader]
            [cljs.spec :as s]
            [re-frame.core :as re-frame]))


;; -- Spec --------------------------------------------------------------------
;;
;; This is a clojure.spec specification for the value in app-db. It is like a
;; Schema. See: http://clojure.org/guides/spec
;;
;; The value in app-db should always match this spec. Only event handlers
;; can change the value in app-db so, after each event handler
;; has run, we re-check app-db for correctness (compliance with the Schema).
;;
;; How is this done? Look in events.cljs and you'll notice that all handers
;; have an "after" interceptor which does the spec re-check.
;;
;; None of this is strictly necessary. It could be omitted. But we find it
;; good practice.

(s/def ::id int?)
(s/def ::title string?)
(s/def ::done boolean?)
(s/def ::todo (s/keys :req-un [::id ::title ::done]))
(s/def ::todos (s/and                                       ;; should use the :kind kw to s/map-of (not supported yet)
                 (s/map-of ::id ::todo)                     ;; in this map, each todo is keyed by its :id
                 #(instance? PersistentTreeMap %)           ;; is a sorted-map (not just a map)
                 ))
(s/def ::showing                                            ;; what todos are shown to the user?
  #{:all                                                    ;; all todos are shown
    :active                                                 ;; only todos whose :done is false
    :done                                                   ;; only todos whose :done is true
    })

;; start editable
(s/def ::inputs map?)
(s/def ::errors map?)
(s/def ::state map?)
(s/def ::defaults map?)
(s/def ::response map?)
(s/def ::formable (s/keys :opt-un [::inputs ::errors ::state ::response ::defaults]))
(s/def ::unique-thing-id (s/or :s string? :k keyword? :i integer? :u uuid?))
(s/def ::identifier (s/every-kv ::unique-thing-id ::formable))
(s/def ::form-type (s/or :s string? :k keyword? :i integer?))
(s/def ::editable (s/nilable (s/every-kv ::form-type ::identifier )))

(comment
  (s/exercise ::unique-thing-id)
  ;; top level is nilable so no data is required to start
  ;; goes like this:
  ;; get-in db [editable form-type identifier :inputs]
  ;; get-in db [:editable :x :y :inputs :z]
  (s/conform ::editable {:x {:y {:inputs {:z 123}}}})
  (s/explain ::editable {:x {:y nil}})

)
;; end editable

(s/def ::db (s/keys :req-un [::todos ::showing] :opt-un [::editable]))

;; -- Default app-db Value  ---------------------------------------------------
;;
;; When the application first starts, this will be the value put in app-db
;; Unless, or course, there are todos in the LocalStore (see further below)
;; Look in `core.cljs` for  "(dispatch-sync [:initialise-db])"
;;

(def default-value                                          ;; what gets put into app-db by default.
  {:todos   (sorted-map)                                    ;; an empty list of todos. Use the (int) :id as the key
   :showing :all
   :editable {:todos {:new {:defaults {:active true :done false}}}}})                                          ;; show all todos


;; -- Local Storage  ----------------------------------------------------------
;;
;; Part of the todomvc challenge is to store todos in LocalStorage, and
;; on app startup, reload the todos from when the program was last run.
;; But the challenge stipulates to NOT  load the setting for the "showing"
;; filter. Just the todos.
;;

(def ls-key "todos-reframe")                          ;; localstore key
(defn todos->local-store
  "Puts todos into localStorage"
  [todos]
  (.setItem js/localStorage ls-key (str todos)))     ;; sorted-map writen as an EDN map


;; register a coeffect handler which will load a value from localstore
;; To see it used look in events.clj at the event handler for `:initialise-db`
(re-frame/reg-cofx
  :local-store-todos
  (fn [cofx _]
      "Read in todos from localstore, and process into a map we can merge into app-db."
      (assoc cofx :local-store-todos
             (into (sorted-map)
                   (some->> (.getItem js/localStorage ls-key)
                            (cljs.reader/read-string)       ;; stored as an EDN map.
                            )))))
