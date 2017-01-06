(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [bones.editable :as e]
            [cljs.spec :as s]))

(defn detect-controls [{:keys [enter escape]}]
  (fn [keypress]
    (case (.-which keypress)
      13 (enter)
      ;; chrome won't fire 27, so use on-blur instead
      27 (escape)
      nil)))

(defn field [form-type identifier attr html-attrs]
  (let [path [:editable form-type identifier :inputs attr]
        value (subscribe path)
        input-type (or (:input-type html-attrs) :input)
        value-attr (or (:value-attr html-attrs) :value)
        opts (dissoc html-attrs :value-attr :input-type)]
    (fn []
      [input-type (merge {:on-change #(dispatch-sync (conj path (-> % .-target .-value)))
                          value-attr @value}
                         opts)])))

(defn checkbox [form-type identifier attr & {:as html-attrs}]
  (field form-type identifier attr (merge {:type "checkbox"
                                           :value-attr :checked}
                                          html-attrs)))

(defn input [form-type identifier attr & {:as html-attrs}]
  (field form-type identifier attr html-attrs))

(defn calculate-command [form-type action]
  (keyword (name form-type) (name action)))

(defn save-fn
  ([[form-type id]]
   ;; call it, save all the inputs
   (apply save-fn id {} {}))
  ([[form-type id] args]
   ;; in case you don't want to pass opts
   (save-fn [form-type id] args {}))
  ([[form-type id] args opts]
   ;; opts can be options like :solo "don't merge values from :inputs"
   (fn []
     (let [new-args (if (fn? args) (args) args)
           action (if (= :new id) :new :update)
           calculated-command (calculate-command form-type action)]
       (dispatch [:request/command calculated-command (merge {:id id} new-args) opts])))))

(defn form
  "returns function as closures around subscriptions to a single 'editable' thing in the
  db. The thing has attributes, whose current value is accessed by calling `inputs' e.g., with arguments. No arguments will return all the attributes"
  [form-type identifier]
  (let [inputs-atom (subscribe [:editable form-type identifier :inputs])
        state-atom (subscribe [:editable form-type identifier :state])
        errors-atom (subscribe [:editable form-type identifier :errors])
        defaults-atom (subscribe [:editable form-type identifier :defaults])
        inputs (fn [& args] (get-in @inputs-atom args))
        state (fn [& args] (get-in @state-atom args))
        errors (fn [& args] (get-in @errors-atom args))
        defaults (fn [& args] (get-in @defaults-atom args))]
    {:inputs inputs
     :state  state
     :errors errors
     :defaults defaults

     :save (partial save-fn [form-type identifier])
     :delete #(dispatch [:request/command (calculate-command form-type :delete) {:id identifier}])
     :reset  #(dispatch (e/editable-reset :todos id (state :reset)))
     :edit   #(dispatch [:editable
                         [:editable :todos identifier :state :reset (inputs)]
                         [:editable :todos identifier :state :editing true]])
     }))

(defn todo-item
  [todo]
  (let [id (get-in todo [:inputs :id])
        {:keys [inputs
                state
                errors
                save
                delete
                reset
                edit]} (form :todos id)]
    (fn []
      [:li {:class (cond-> ""
                     (inputs :done)   (str " completed")
                     (state :pending) (str " pending")
                     (state :editing) (str " editing"))}
       [:div.view
        [checkbox :todos id :done
         :class "toggle"
         ;; here we rely on the response from the server to update the form
         ;; :solo means only send these attributes, and only update these
         ;; attributes in the response handler
         :on-change (save (fn [] {:done (not (inputs :done))}) {:solo true})]
        [:label
         ;; edit will "toggle" :editing
         {:on-double-click edit}
         (inputs :todo)]
        [:button.destroy
         {:on-click delete}]]
       (when (state :editing)
         ;; reset and save will "toggle" :editing
         [input :todos id :todo
          :id (str id "-id")
          :class "edit"
          :on-blur reset
          :on-key-down (detect-controls {:enter (save {:done false})
                                         :escape reset})])])))

(defn task-list
  []
  (let [visible-todos (subscribe [:visible-todos])
        all-complete? (subscribe [:all-complete?])]
    (fn []
      [:section#main
       [:input#toggle-all
        {:type "checkbox"
         :checked @all-complete?
         :on-change #(dispatch [:complete-all-toggle (not @all-complete?)])}]
       [:label
        {:for "toggle-all"}
        "Mark all as complete"]
       [:ul#todo-list
        (for [todo @visible-todos]
          ^{:key ((comp :id :inputs) todo)} [todo-item todo])]])))

(defn footer-controls
  []
  (let [[active done] @(subscribe [:footer-counts])
        showing       @(subscribe [:editable :todos :_meta :filter])
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw showing) "selected")
                             :href (if filter-kw (str "#/" (name filter-kw)) "#/")} txt])]
    [:footer#footer
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn nil    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done)
       [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
        "Clear completed"])]))

(defn task-entry []
  (let [new-todo (subscribe [:editable :todos :new])
        defaults (subscribe [:editable :todos :new :defaults])
        stop #(dispatch (e/editable-reset :todos :new @defaults))
        ;; the defaults will get merged into the inputs before sending
        ;; also, the response handler needs the defaults to reset the inputs to
        save #(dispatch [:request/command :todos/new :new {:tap {:defaults @defaults}
                                                           :identifier :new}])]
    (fn []
      [input :todos :new :todo
       :id "new-todo"
       :placeholder "What needs to be done?"
       :auto-focus true
       :on-blur stop
       :on-key-down (detect-controls {:enter save
                                      :escape stop})])))

(defn todo-app
  []
  [:div
   [:section#todoapp
    [:header#header
     [:h1 "todos"]
     [task-entry]]
    (when (seq @(subscribe [:editable :todos]))
      [task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])

(defmethod e/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [
        ;; - extract the info passed from the dispatcher
        ;; - the form-type and indentifier are required
        ;; - identifier may be ":new", as in the new form without an id
        ;; - the defaults are passed from the new dispatcher so we can reset the
        ;;   new form.
        ;; - the defaults may or may not be passed
        {:keys [form-type identifier defaults]} tap
        ;; these keys should be put into tap as well, maybe
        {:keys [command args]} response
        ;; - this is the new or existing id
        {:keys [id]} args]
    (let [commandspace (namespace command)
          action (name command)]
      (if (and commandspace (contains? args :id))
        ;; apply conventions
        (condp = action
          "new"
          ;; reset new form to defaults
          ;; insert new thing with id into db
          {:dispatch (e/editable-response form-type identifier response defaults)
           :db (assoc-in db [:editable form-type id :inputs] args)}
          "update"
          (if (:solo tap)
            ;; merge :args into :inputs
            {:db(update-in db [:editable form-type id :inputs] merge args)
             :dispatch [:editable form-type id :state :pending false]}
            ;; reset :inputs to :args
            {:dispatch (e/editable-response form-type identifier response args)})
          "delete"
          {:db (update-in db [:editable form-type] dissoc identifier)}
          )
        ;; this will probably need to be overridden somehow
        ;; maybe this should be another multimethod
        {:dispatch (e/editable-response form-type identifier response)}))))


(comment

  @re-frame.db/app-db

  )
