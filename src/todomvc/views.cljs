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

(defn todo-item
  [todo]
  (let [id (get-in todo [:inputs :id])
        inputs (subscribe [:editable :todos id :inputs])
        done (subscribe [:editable :todos id :inputs :done])
        label (subscribe [:editable :todos id :inputs :todo])
        errors (subscribe [:editable :todos id :errors])
        state (subscribe [:editable :todos id :state])
        reset #(dispatch (e/editable-reset :todos id (:reset @state)))
        incomplete #(dispatch [:editable :todos id :inputs :done false])
        toggle #(dispatch [:editable :todos id :inputs :done (not %)])
        save #(dispatch [:request/command :todos id {:command :todos/update}])
        delete #(dispatch [:request/command :todos id {:command :todos/delete}])
        edit #(dispatch [:editable
                         [:editable :todos id :state :reset @inputs]
                         [:editable :todos id :state :editing true]])]
    (fn []
      [:li {:class (str (when @done "completed ")
                        (when (:editing @state) "editing"))}
       [:div.view
        [checkbox :todos id :done
         :class "toggle"
         :on-change #(do (toggle @done) (save))]
        [:label
         {:on-double-click edit}
         @label]
        [:button.destroy
         {:on-click delete}]]
       (when (:editing @state)
         [input :todos id :todo
          :id (str id "-id")
          :class "edit"
          :on-blur reset
          :on-key-down (detect-controls {:enter #(do (incomplete) (save))
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
        stop #(dispatch (e/editable-reset :todos :new (:defaults @new-todo)))
        ;; the defaults will need to get submitted along with the inputs, right?
        ;; the response handler needs the defaults to reset the inputs to
        save #(dispatch [:request/command :todos :new {:command :todos/new
                                                       :tap {:defaults (:defaults @new-todo)}}])]
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
          {:dispatch (e/editable-response form-type identifier response args)}
          "delete"
          {:db (update-in db [:editable form-type] dissoc identifier)}
          )
        ;; this will probably need to be overridden somehow
        ;; maybe this should be another multimethod
        {:dispatch (e/editable-response form-type identifier response)}))))


(comment

  @re-frame.db/app-db

  )
