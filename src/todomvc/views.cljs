(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [bones.editable :as e]))

(defn todo-item
  [todo]
  (let [id (get-in todo [:inputs :id])
        {:keys [inputs
                state
                errors
                save
                delete
                reset
                edit]} (e/form :todos id)]
    (fn []
      [:li {:class (cond-> ""
                     (inputs :done)   (str " completed")
                     (state :pending) (str " pending")
                     (state :editing) (str " editing"))}
       [:div.view
        [e/checkbox :todos id :done
         :class "toggle"
         ;; here we rely on the response from the server to update the form
         ;; sending :args with no :merge here means
         ;;   only send these attributes
         :on-change (save (fn [] {:args {:id id :done (not (inputs :done))}}))]
        [:label
         ;; edit will set (state :editing)
         {:on-double-click (edit :todo)}
         (inputs :todo)]
        [:button.destroy
         {:on-click delete}]]
       (when (state :editing)
         ;; reset and save will unset (state :editing)
         [e/input :todos id :todo
          :id (str id "-id")
          :class "edit"
          :on-blur reset
          ;; behavior specified is that an edited todo, is no longer a done todo
          :on-key-down (e/detect-controls {:enter (save {:args {:done false} :merge [:inputs]})
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
          ;; get the unique id from the todo's attributes
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
  (let [{:keys [reset save]} (e/form :todos :new)]
    (fn []
      [e/input :todos :new :todo
       :id "new-todo"
       :placeholder "What needs to be done?"
       :auto-focus true
       :on-blur reset
       :on-key-down (e/detect-controls {:enter (save {:args {} :merge [:inputs :defaults]})
                                        :escape reset})])))

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
