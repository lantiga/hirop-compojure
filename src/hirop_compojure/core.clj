(ns hirop-compojure.core
  (:use compojure.core
        hirop.stateful
        [ring.util.response :only [response redirect status]]))

(defmacro hirop-routes-with-prefix
  [prefix]
  `(context ~prefix [] hirop-routes))

(def vec-response
  (comp response vec))

(defroutes hirop-routes
  (GET "/doctypes" []
       (response (doctypes)))
  (GET "/contexts" []
       (response (contexts)))
  (POST "/clean-contexts" []
        (response (clean-contexts)))
  (POST "/clean-context" {{context-id :context-id} :params}
        (response (clean-context :context-id context-id)))
  (POST "/create-context" {{context-name :name external-ids :external-ids meta :meta context-id :context-id} :params}
        (response (create-context context-name external-ids meta :context-id context-id)))
  ;; TODO: if selection-id in params, then fill document with currently selected relations
  (GET "/new-document" {{doctype :doctype context-id :context-id} :params}
       (response (new-document doctype :context-id context-id)))
  ;; TODO: if selection-id in params, then fill documents with currently selected relations
  (GET "/new-documents" {{doctype-map :doctype-map context-id :context-id} :params}
       (response (new-documents doctype-map :context-id context-id)))
  (GET "/get-document" {{id :id context-id :context-id} :params}
       (response (get-document id :context-id context-id)))
  (GET "/get-external-documents" {{context-id :context-id} :params}
       (vec-response (get-external-documents :context-id context-id)))
  (GET "/get-configurations" {{context-id :context-id} :params}
       (response (get-configurations :context-id context-id)))
  (GET "/get-configuration" {{doctype :doctype context-id :context-id} :params}
       (response (get-configuration doctype :context-id context-id)))
  (GET "/get-doctype" {{doctype :doctype context-id :context-id} :params}
       (response (get-doctype doctype :context-id context-id)))
  (GET "/get-baseline" {{id :id context-id :context-id} :params}
       (response (get-baseline id :context-id context-id)))
  (POST "/commit" {{document :document context-id :context-id} :params}
        (response (commit document :context-id context-id)))
  (POST "/mcommit" {{documents :documents context-id :context-id} :params}
        (response (mcommit documents :context-id context-id)))
  (POST "/pull" {{context-id :context-id} :params}
        (response (pull :context-id context-id)))
  (GET "/get-conflicted-ids" {{context-id :context-id} :params}
       (vec-response (get-conflicted-ids :context-id context-id)))
  (GET "/any-conflicted" {{context-id :context-id} :params}
       (response (any-conflicted :context-id context-id)))
  (GET "/checkout-conflicted" {{context-id :context-id} :params}
       (response (checkout-conflicted :context-id context-id)))
  (POST "/push" {{context-id :context-id} :params}
        (response (push :context-id context-id)))
  (POST "/save" {{documents :documents context-id :context-id} :params}
        (response (save :context-id context-id)))
  (GET "/history" {{id :id context-id :context-id} :params}
       (vec-response (history id :context-id context-id)))
  (GET "/checkout" {{doctype :doctype context-id :context-id} :params}
       (if doctype
         (vec-response (checkout :doctype doctype :context-id context-id))
         (response (checkout :context-id context-id))))
  (GET "/get-selected-ids" {{selection-id :selection-id doctype :doctype context-id :context-id} :params}
       (if doctype
         (vec-response (get-selected-ids :selection-id selection-id :doctype doctype :context-id context-id))
         (response (get-selected-ids :selection-id selection-id :context-id context-id))))
  (GET "/checkout-selected" {{doctype :doctype selection-id :selection-id context-id :context-id} :params}
       (if doctype
         (vec-response (checkout-selected :selection-id selection-id :doctype doctype :context-id context-id))
         (response (checkout-selected :selection-id selection-id :context-id context-id))))
  (POST "/select" {{id :id selection-id :selection-id context-id :context-id} :params}
        (vec-response (select :selection-id selection-id :id id :context-id context-id)))
  (POST "/unselect" {{selection-id :selection-id doctype :doctype context-id :context-id} :params}
        (vec-response (unselect :selection-id selection-id :doctype doctype :context-id context-id))))

