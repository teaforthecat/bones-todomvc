(ns todomvc.db
  (:require [cljs.reader]
            [cljs.spec :as s]
            [re-frame.core :as re-frame]))


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

;; end editable

(s/def ::db (s/keys :opt-un [::editable]))

(def default-value
  {:editable {:todos {:new {:defaults {:active true :done false}}}}})




(def ls-key "todos-reframe")                          ;; localstore key
(defn todos->local-store
  "Puts todos into localStorage"
  [todos]
  (.setItem js/localStorage ls-key (str todos)))     ;; sorted-map writen as an EDN map

(re-frame/reg-cofx
  :local-store-todos
  (fn [cofx _]
      "Read in todos from localstore, and process into a map we can merge into app-db."
      (assoc cofx :local-store-todos
             (into (sorted-map)
                   (some->> (.getItem js/localStorage ls-key)
                            (cljs.reader/read-string)       ;; stored as an EDN map.
                            )))))
