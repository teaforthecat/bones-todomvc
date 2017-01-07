(ns todomvc.views
  (:require [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch dispatch-sync]]
            [bones.editable :as e]
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
         [e/input :todos id :todo
          :id (str id "-id")
          :class "edit"
          :on-blur reset
          :on-key-down (e/detect-controls {:enter (save {:done false})
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
  (let [{:keys [reset save]} (e/form :todos :new)]
    (fn []
      [e/input :todos :new :todo
       :id "new-todo"
       :placeholder "What needs to be done?"
       :auto-focus true
       :on-blur reset
       :on-key-down (e/detect-controls {:enter save
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

(defmethod e/handler [:response/command 200]
  [{:keys [db]} [channel response status tap]]
  (let [
        ;; - extract the info passed from the dispatcher
        ;; - the form-type and indentifier are required
        ;; - identifier may be ":new", as in the new form without an id
        ;; - the defaults are passed from the new dispatcher so we can reset the
        ;;   new form.
        ;; - the defaults may or may not be passed
        {:keys [form-type identifier]} tap
        defaults (if (= identifier :new)
                   (get-in db [:editable form-type identifier :defaults]))
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
