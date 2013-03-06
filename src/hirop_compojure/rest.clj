(ns hirop-compojure.rest
  (:use compojure.core
        hirop.core
        hirop.protocols
        [clojure.walk :only [keywordize-keys]]
        [ring.util.response :only [response status]])
  (:require [hirop.backend :as backend]
            [ring.mock.request :as mock]
            [cheshire.core :as json]))

(def ^:dynamic *get-hirop-conf*)

(defn- get-store [req]
  (get-in req [:hirop :store]))

(defn- init-context-in-store
  [context-name external-ids store]
  (let [{contexts :contexts doctypes :doctypes meta :meta backend :backend} (*get-hirop-conf*)]
    (put-context
     store
     (init-context context-name (get contexts context-name) doctypes external-ids meta backend))))

(defroutes hirop-routes

  (POST "/contexts" {{context-name :context-name external-ids :external-ids} :params :as req}
        (let [context-id (init-context-in-store (keyword context-name) external-ids (get-store req))]
          (response {:context-id context-id})))

  (context "/contexts/:context-id" [context-id]

           (DELETE "/" req
                   (->
                    (delete-context (get-store req) context-id)
                    response))

           (POST "/push" req
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

           (POST "/pull" req
                 (let [context
                       (update-context (get-store req) context-id
                                       #(pull % (partial backend/fetch (get % :backend))))]
                   (->
                    {:result (if (any-conflicted? context) :conflict :success)}
                    response)))

           (GET "/external" req
                (->
                 (get-context (get-store req) context-id)
                 (get :external-ids)
                 response))

           (GET "/doctypes" req
                (let [context (get-context (get-store req) context-id)]
                  (->>
                   (map (fn [doctype] [doctype (get-doctype context (keyword doctype))]) (keys (get context :doctypes)))
                   (into {})
                   response)))

           (GET "/doctypes/:doctype" [doctype :as req]
                (->
                 (get-context (get-store req) context-id)
                 (get-doctype (keyword doctype))
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

           (HEAD "/conflicted" req
                 (let [context (get-context (get-store req) context-id)]
                   (if (any-conflicted? context)
                     (response nil)
                     (-> (response nil) (status 404)))))

           (GET "/conflicted" req
                (->
                 (get-context (get-store req) context-id)
                 checkout-conflicted
                 response))

           (POST "/conflicted" req
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

           (POST "/documents" req
                 (let [doc (get-in req [:params :document])
                       docs (get-in req [:params :documents])]
                   (cond
                    doc (do (update-context (get-store req) context-id #(commit % doc)) (response "1"))
                    docs (do (update-context (get-store req) context-id #(mcommit % docs)) (response (str (count docs))))
                    :else (response nil))))

           (GET "/documents" req
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

                    (GET "/documents" req
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

                    (GET "/ids" req
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

                    (POST "/" req
                         (update-context (get-store req) context-id #(select-defaults % (keyword selection-id)))
                         (response nil))

                    (POST "/:doc-id" [doc-id :as req]
                         (update-context (get-store req) context-id #(select-document % doc-id (keyword selection-id)))
                         (response nil))

                    (DELETE "/:doctype" [doctype :as req]
                            (update-context (get-store req) context-id #(unselect % (keyword selection-id) (keyword doctype)))
                            (response nil)))))

(defn- command-request
  [method uri params store]
  (->
    (mock/request method uri)
    (update-in [:params] merge (keywordize-keys params))
    (assoc-in [:hirop :store] store)))

(defn- perform-commands
  [context-id commands req]
  (let [uri-prefix (str "/contexts/" context-id)]
    (vec (map
          (fn [[method uri params]]
            (let [method (keyword method)
                  uri (str uri-prefix uri)]
              (->
               (command-request method uri params (get-store req))
               (hirop-routes)
               :body)))
          commands))))


(defroutes commands-routes
  (POST "/contexts/:context-id/commands" {{context-id :context-id} :params :as req}
        (let [commands (get-in req [:json-params "commands"])
              responses (perform-commands context-id commands req)]
          (response {:responses responses})))

  (POST "/contexts/commands" {{context-name :context-name external-ids :external-ids} :params :as req}
        (let [commands (get-in req [:json-params "commands"])
              context-id (init-context-in-store (keyword context-name) external-ids (get-store req))
              responses (perform-commands context-id commands req)]
          (response {:context-id context-id :responses responses}))))


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
