(ns hirop-compojure.rest
  (:use compojure.core
        hirop.core
        hirop.protocols
        [ring.util.response :only [response status]])
  (:require [hirop.backend :as backend]))

(def ^:dynamic *get-hirop-conf*)

(defn- get-store [req]
  (get-in req [:hirop :store]))

(defroutes hirop-routes

  (POST "/contexts" [:as req]
        (let [context-name (keyword (get-in req [:params :context-name]))
              external-ids (get-in req [:params :external-ids])
              {contexts :contexts doctypes :doctypes meta :meta backend :backend} (*get-hirop-conf*)
              context-id
              (put-context (get-store req)
                           (init-context context-name (get contexts context-name) doctypes external-ids meta backend))]
          (response {:context-id context-id})))

  (context "/contexts/:context-id" [context-id]

           (DELETE "/" [:as req]
                   (->
                    (delete-context (get-store req) context-id)
                    response))

           (POST "/push" [:as req]
                 (let [context (get-context (get-store req) context-id)
                       save-info
                       (when context
                         (push-save context (partial backend/save (get context :backend))))]
                   (->>
                    (update-context (get-store req) context-id
                                    #(push-post-save % save-info))
                    get-push-result
                    (assoc {} :result)
                    response)))

           (POST "/pull" [:as req]
                 (let [context
                       (update-context (get-store req) context-id
                                       #(pull % (partial backend/fetch (get % :backend))))]
                   (->
                    {:result (if (any-conflicted? context) :conflict :success)}
                    response)))

           (GET "/external" [:as req]
                (->
                 (get-context (get-store req) context-id)
                 (get :external-ids)
                 response))

           (GET "/configurations" [:as req]
                (->
                 (get-context (get-store req) context-id)
                 (get :configurations)
                 response))

           (GET "/configurations/:doctype" [doctype :as req]
                (->
                 (get-context (get-store req) context-id)
                 (get-in [:configurations (keyword doctype)])
                 response))

           (GET "/doctypes" [:as req]
                (->
                 (get-context (get-store req) context-id)
                 (get :doctypes)
                 response))

           (GET "/doctypes/:doctype" [doctype :as req]
                (->
                 (get-context (get-store req) context-id)
                 (get-in [:doctypes (keyword doctype)])
                 response))

           (GET "/current/:doc-id" [doc-id :as req]
                (->
                 (get-context (get-store req) context-id)
                 (get-document doc-id)
                 response))

           (GET "/baseline/:doc-id" [doc-id :as req]
                (->
                 (get-context (get-store req) context-id)
                 (get-baseline doc-id)
                 response))

           (GET "/stored/:doc-id" [doc-id :as req]
                (->
                 (get-context (get-store req) context-id)
                 (get-stored doc-id)
                 response))

           (GET "/history/:doc-id" [doc-id :as req]
                (let [context (get-context (get-store req) context-id)]
                  (->
                   (backend/history context (get context :backend) doc-id)
                   vec
                   response)))

           (HEAD "/conflicted" [:as req]
                 (let [context (get-context (get-store req) context-id)]
                   (if (any-conflicted? context)
                     (response nil)
                     (-> (response nil) (status 404)))))

           (GET "/conflicted" [:as req]
                (->
                 (get-context (get-store req) context-id)
                 checkout-conflicted
                 response))

           (POST "/conflicted" [:as req]
                 (let [doc (get-in req [:params :document])
                       docs (get-in req [:params :documents])]
                   (cond
                    doc (do (update-context (get-store req) context-id #(commit-conflicted % doc)) (response "1"))
                    docs (do (update-context (get-store req) context-id #(mcommit-conflicted % docs)) (response (str (count docs))))
                    :else (response nil))))

           (GET "/documents/new/:doctype" [doctype :as req]
                (->
                 (get-context (get-store req) context-id)
                 (new-document (keyword doctype))
                 response))

           (POST "/documents" [:as req]
                 (let [doc (get-in req [:params :document])
                       docs (get-in req [:params :documents])]
                   (cond
                    doc (do (update-context (get-store req) context-id #(commit % doc)) (response "1"))
                    docs (do (update-context (get-store req) context-id #(mcommit % docs)) (response (str (count docs))))
                    :else (response nil))))

           (GET "/documents" [:as req]
                (->
                 (get-context (get-store req) context-id)
                 checkout
                 vec
                 response))

           (GET "/documents/:doctype" [doctype :as req]
                (->
                 (get-context (get-store req) context-id)
                 (checkout (keyword doctype))
                 vec
                 response))

           (context "/selected/:selection-id" [selection-id]

                    (GET "/documents" [:as req]
                         (->
                          (get-context (get-store req) context-id)
                          (checkout-selected (keyword selection-id))
                          response))

                    (GET "/documents/:doctype" [doctype :as req]
                         (->
                          (get-context (get-store req) context-id)
                          (checkout-selected (keyword selection-id) (keyword doctype))
                          vec
                          response))

                    (GET "/ids" [:as req]
                         (->
                          (get-context (get-store req) context-id)
                          (get-selected-ids (keyword selection-id))
                          response))

                    (GET "/ids/:doctype" [doctype :as req]
                         (->
                          (get-context (get-store req) context-id)
                          (get-selected-ids (keyword selection-id) (keyword doctype))
                          vec
                          response))

                    ;; TODO: check about the selection change function,
                    ;; eventually move it to core and call it from here

                    (POST "/" [:as req]
                         (update-context (get-store req) context-id #(select-defaults % (keyword selection-id)))
                         (response nil))

                    (POST "/:doc-id" [doc-id :as req]
                         (update-context (get-store req) context-id #(select-document % doc-id (keyword selection-id)))
                         (response nil))

                    (DELETE "/:doctype" [doctype :as req]
                            (update-context (get-store req) context-id #(unselect % (keyword selection-id) (keyword doctype)))
                            (response nil)))

           ))

;; all GET's have Expires: 0 or Cache-Control: no-cache in the header (except configurations, external-documents and doctype)

;; Use HTTP response codes, e.g. 409 Conflict, 404 Not Found, 403 Permission Denied
;; Use headers for return error messages, in Status Text or Warning header (actually not mandatory)

;;  (defn- change-selection [context-id selection-id session-updater]
;;  (let [old-selection (get-in (session/get-in [:store context-id]) [:selections (keyword selection-id)])
;;        session (session/update! session-updater)
;;        new-selection (get-in session [context-id :store :selections (keyword selection-id)])
;;        changed-selection (set (map first (filter (fn [[k v]] (not= v (get old-selection k))) new-selection)))
;;        removed (set (map first (filter (fn [[k _]] (not (contains? new-selection k))) old-selection)))]
;;    (union changed-selection removed)))
