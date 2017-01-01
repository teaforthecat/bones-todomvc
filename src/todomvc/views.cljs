(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [bones.editable :as e]
            [cljs.spec :as s]))

(defn detect-controls [{:keys [enter escape]}]
  (fn [keypress]
    (case (.-which keypress)
      13 (enter)
      ;; chrome won't fire 27, so use on-blur instead
      27 (escape)
      nil)))

(defn todo-item
  [todo]
  (let [id (get-in todo [:inputs :id])
        todo-form (subscribe [:editable :todos id])]
    (fn []
      (let [{:keys [inputs errors state]} @todo-form ;; this will redraw the whole component on every character typed
            stop #(dispatch (e/editable-reset :todos id (:reset state)))
            ;; editing a completed tasks makes it incomplete automatically
            incomplete #(dispatch [:editable :todos id :inputs :done false])
            save #(dispatch [:request/command :todos id])]
        [:li {:class (str (when (:done inputs) "completed ")
                          (when (:editing state) "editing"))}
         [:div.view
          [:input.toggle
           {:type "checkbox"
            :checked (:done inputs)
            :on-change #(dispatch [:editable :todos id :inputs :done (not (:done inputs))])}]
          [:label
           {:on-double-click #(dispatch [:editable
                                         [:editable :todos id :state :reset inputs]
                                         [:editable :todos id :state :editing true]])}
           (:todo inputs)]
          [:button.destroy
           {:on-click #(dispatch [:request/command :todos id {:command :delete-todo}])}]]
         (when (:editing state)
           [:input {:id (str (:id inputs) "-id")
                    :class "edit"
                    :value (:todo inputs)
                    :on-change #(dispatch [:editable :todos id :inputs :todo (-> % .-target .-value)])

                    :on-blur stop
                    :on-key-down (detect-controls {:enter #(do (incomplete) (save))
                                                   :escape stop})}]
           )]))))

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
          ;;TODO: oh noes remove :new here
          ^{:key (or (get-in todo [:inputs :id]) :new)} [todo-item todo])]])))

(defn footer-controls
  []
  (let [[active done] @(subscribe [:footer-counts])
        showing       @(subscribe [:showing])
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw showing) "selected")
                             :href (str "#/" (name filter-kw))} txt])]
    [:footer#footer
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn :all    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done)
       [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
        "Clear completed"])]))

(defn task-entry []
  (let [new-todo (subscribe [:editable :todos :new])
        stop #(dispatch (e/editable-reset :todos :new (:defaults @new-todo)))
        ;; do defaults differently I think
        save #(dispatch [:request/command :todos :new {:tap {:defaults (:defaults @new-todo)}}])]
    (fn []
      [:input {:id "new-todo"
               :placeholder "What needs to be done?"
               :value (get-in @new-todo [:inputs :todo])
               :on-change #(dispatch [:editable :todos :new :inputs :todo (-> % .-target .-value)])
               :auto-focus true
               :on-blur stop
               :on-key-down (detect-controls {:enter save
                                              :escape stop})}])))
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
  (let [{:keys [form-type identifier defaults]} tap
        {:keys [command args]} response
        ;; reset the inputs from the server? maybe.
        ;; reset the inputs for the new form to the defaults? definitely.
        inputs (if (= :new identifier) defaults args)
        ;; this may be the new id from the server
        {:keys [id]} args]
    (condp = command
      :delete-todo
      {:db (update-in db [:editable form-type] dissoc identifier)}
      :todos
      {:dispatch (e/editable-response form-type identifier response inputs)
       ;; upsert inputs
       :db (assoc-in db [:editable form-type id :inputs] args)})))


(comment

  @re-frame.db/app-db

  )
