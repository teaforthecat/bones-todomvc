(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [bones.editable :as e]
            [bones.editable.helpers :as h]
            [bones.editable.forms :as f]
            [bones.editable.request :as request]
            [bones.editable.response :as response]
            [bones.editable.subs :as subs]
            [cljs.spec :as s]))

(defn todo-item
  [todo]
  (let [id (get-in todo [:inputs :id])
        {:keys [inputs
                state
                errors
                save
                delete
                reset
                edit]} (f/form :todos id)]
    (fn []
      [:li {:class (cond-> ""
                     (inputs :done)   (str " completed")
                     (state :pending) (str " pending")
                     (state :editing) (str " editing"))}
       [:div.view
        [f/checkbox :todos id :done
         :class "toggle"
         ;; here we rely on the response from the server to update the form
         ;; no merge here means only send these attributes, and only update these
         ;; attributes in the response handler
         :on-change (save (fn [] {:args {:done (not (inputs :done))}}))]
        [:label
         ;; edit will "toggle" :editing
         {:on-double-click edit}
         (inputs :todo)]
        [:button.destroy
         {:on-click delete}]]
       (when (state :editing)
         ;; reset and save will "toggle" :editing by clearing state
         [f/input :todos id :todo
          :id (str id "-id")
          :class "edit"
          :on-blur reset
          :on-key-down (f/detect-controls {:enter (save {:args {:done false}})
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
  (let [{:keys [reset save]} (f/form :todos :new)]
    (fn []
      [f/input :todos :new :todo
       :id "new-todo"
       :placeholder "What needs to be done?"
       :auto-focus true
       :on-blur reset
       :on-key-down (f/detect-controls {:enter (save {:args {} :merge [:inputs :defaults]})
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

(defmethod response/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [
        ;; - extract the info passed from the dispatcher
        ;; - the form-type and indentifier are required
        ;; - identifier may be ":new", as in the new form without an id
        ;; - the defaults are passed from the new dispatcher so we can reset the
        ;;   new form.
        ;; - the defaults may or may not be passed
        {:keys [command args e-scope]} tap
        [_ e-type identifier] e-scope
        ;; this has already happened I think:
        ;; defaults (if (= identifier :new)
        ;;            (get-in db [:editable form-type identifier :defaults]))
        ;; these keys should be put into tap as well, maybe
        ;; redundant, but it is nice to close the loop
        ;;{:keys [comma args]} response
        ;; - this is the new or existing id
        {:keys [id]} args]

    ;; apply conventions
    (condp = command
      :todos/new
      ;; reset new form to defaults
      ;; insert new thing with id into db
      {:dispatch (h/editable-response e-type identifier response)
       :db (assoc-in db [:editable e-type id :inputs] args)}
      :todos/update
      ;; merge :args into :inputs
      {:db (update-in db [:editable e-type id :inputs] merge args)
       :dispatch [:editable e-type id :state :pending false]}
      ;; reset :inputs to :args
      ;; {:dispatch (h/editable-response e-type identifier response args)}
      :todos/delete
      {:db (update-in db [:editable e-type] dissoc identifier)}
      :todos/delete-many
      {:db (update-in db [:editable e-type] #(reduce dissoc % (:ids args)))})))

(defmethod response/handler [:response/query 200]
  [{:keys [db]} [channel response status tap]]
  {:db (update-in db [:editable :todos] merge (:results response))})



(comment

  (get-in   @re-frame.db/app-db [:editable :todos])

  )
