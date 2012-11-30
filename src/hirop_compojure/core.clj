(ns hirop-compojure.core
  (:use compojure.core
        hirop.session-frontend
        [ring.util.response :only [response redirect status]]))

;; TODO: extend to multiple concurrent contexts

;; TODO: make a couch embedded backend

(defmacro hirop-routes-with-context
  [prefix]
  `(context ~prefix [] hirop-routes))

(def vec-response
  (comp response vec))

(defroutes hirop-routes
  (GET "/doctypes" []
       (response (doctypes)))
  (GET "/contexts" []
       (response (contexts)))
  (POST "/clean-context" []
        (clean-context))
  (POST "/init-context" {{context-name :name external-ids :external-ids meta :meta} :params}
        (init-context context-name external-ids meta))
  ;; TODO: if selection-id in params, then fill document with currently selected relations
  (GET "/new-document" {{doctype :doctype} :params}
       (response (new-document doctype)))
  ;; TODO: if selection-id in params, then fill documents with currently selected relations
  (GET "/new-documents" {{doctype-map :doctype-map} :params}
       (response (new-documents doctype-map)))
  (GET "/get-document" {{id :id} :params}
       (response (get-document id)))
  (GET "/get-external-documents" []
       (vec-response (get-external-documents)))
  (GET "/get-configurations" []
       (response (get-configurations)))
  (GET "/get-configuration" {{doctype :doctype} :params}
       (response (get-configuration doctype)))
  (GET "/get-doctype" {{doctype :doctype} :params}
       (response (get-doctype doctype)))
  (GET "/get-baseline" {{id :id} :params}
       (response (get-baseline id)))
  (POST "/commit" {{document :document} :params}
        (commit document))
  (POST "/mcommit" {{documents :documents} :params}
        (mcommit documents))
  (POST "/pull" []
        (response (pull)))
  (GET "/get-conflicted-ids" []
       (vec-response (get-conflicted-ids)))
  (GET "/any-conflicted" []
       (response (any-conflicted)))
  (GET "/checkout-conflicted" []
       (response (checkout-conflicted)))
  (POST "/push" []
        (response (push)))
  (POST "/save" {{documents :documents} :params}
        (response (save)))
  (GET "/history" {{id :id} :params}
       (vec-response (history id)))
  (GET "/checkout" {{doctype :doctype} :params}
       (if doctype
         (vec-response (checkout doctype))
         (response (checkout))))
  (GET "/get-selected-ids" {{selection-id :selection-id doctype :doctype} :params}
       (if doctype
         (vec-response (get-selected-ids selection-id doctype))
         (response (get-selected-ids selection-id))))
  (GET "/checkout-selected" {{doctype :doctype selection-id :selection-id} :params}
       (if doctype
         (vec-response (checkout-selected selection-id doctype))
         (response (checkout-selected selection-id))))
  (POST "/select" {{id :id selection-id :selection-id} :params}
        (vec-response (select selection-id id)))
  (POST "/unselect" {{selection-id :selection-id doctype :doctype} :params}
        (vec-response (unselect selection-id doctype))))

