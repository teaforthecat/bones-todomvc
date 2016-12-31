(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [bones.editable :as e]
            [cljs.spec :as s]))


(defn todo-input [{:keys [title on-save on-stop]}]
  (let [val (reagent/atom title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (when (seq v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge props
                     {:type "text"
                      :value @val
                      :auto-focus true
                      :on-blur save
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                     13 (save)
                                     27 (stop)
                                     nil)})])))


(defn todo-item
  []
  (let [editing (reagent/atom false)]
    (fn [{:keys [id done title]}]
      [:li {:class (str (when done "completed ")
                        (when @editing "editing"))}
        [:div.view
          [:input.toggle
            {:type "checkbox"
             :checked done
             :on-change #(dispatch [:toggle-done id])}]
          [:label
            {:on-double-click #(reset! editing true)}
            title]
          [:button.destroy
            {:on-click #(dispatch [:delete-todo id])}]]
        (when @editing
          [todo-input
            {:class "edit"
             :title title
             :on-save #(dispatch [:save id %])
             :on-stop #(reset! editing false)}])])))

(defn e-todo-item
  [todo]
  (let [id (get-in todo [:inputs :id])
        todo-form (subscribe [:editable :todos id])]
    (fn []
      (let [{:keys [inputs errors state]} @todo-form ;; this will redraw the whole component on every character typed
            stop #(dispatch (e/editable-reset :todos id (:reset state)))
            save #(dispatch [:request/command :todos id])]
        [:li {:class (str (when (:done inputs) "completed ")
                          (when (:editing state) "editing"))}
         [:div.view
          [:input.toggle
           {:type "checkbox"
            :checked (:done inputs)
            :on-change #(dispatch [:editable :todos id :inputs :done (-> % .-target .-value)])}]
          [:label
           {:on-double-click #(dispatch [:editable
                                         [:editable :todos id :state :reset inputs]
                                         [:editable :todos id :state :editing true]])}
           (:todo inputs)]
          [:button.destroy
           {:on-click #(dispatch [:request/command :delete-todo {:id id}])}]]
         (when (:editing state)
           [:input {:id (str (:id inputs) "-id")
                    :value (:todo inputs)
                    :on-change #(dispatch [:editable :todos id :inputs :todo (-> % .-target .-value)])
                    :on-key-down #(case (.-which %)
                                    13 (save)
                                    27 (stop)
                                    nil)}]
           )]))))

(defn task-list
  []
  (let [visible-todos @(subscribe [:visible-todos])
        all-complete? @(subscribe [:all-complete?])]
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked all-complete?
           :on-change #(dispatch [:complete-all-toggle (not all-complete?)])}]
        [:label
          {:for "toggle-all"}
          "Mark all as complete"]
        [:ul#todo-list
          (for [todo  visible-todos]
            ^{:key (:id todo)} [todo-item todo])]]))

(defn e-task-list
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
          ^{:key (or (get-in todo [:inputs :id]) :new)} [e-todo-item todo])]])))

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


(defn task-entry
  []
  [:header#header
    [:h1 "todos"]
    [todo-input
      {:id "new-todo"
       :placeholder "What needs to be done?"
       :on-save #(dispatch [:add-todo %])}]])


(defn e-task-entry []
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
               ;; :on-blur save ;; on-blur will fire when switching windows so we won't use it
               :on-key-down #(case (.-which %)
                               13 (save)
                               27 (stop)
                               nil)}])))

(defmethod e/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [{:keys [form-type identifier]} tap
        {:keys [id]} (:args response)]
    {:dispatch (e/editable-response form-type identifier response)
     :db (assoc-in db [:editable form-type id :inputs] (:args response))}))

(comment

  @re-frame.db/app-db

  )

(defn todo-app
  []
  [:div
   [:section#todoapp
    [:header#header
     [:h1 "todos"]
     [e-task-entry]]
    (when (seq @(subscribe [:editable :todos]))
      [e-task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
