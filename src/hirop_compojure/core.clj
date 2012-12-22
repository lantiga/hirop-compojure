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
  (GET "/get-doctypes" []
       (response (doctypes)))
  (GET "/get-contexts" []
       (response (contexts)))
  (POST "/clean-contexts" []
        (clean-contexts))
  (POST "/clean-context" {{context-id :context-id} :params}
        (clean-context :context-id context-id))
  (POST "/create-context" {{context-name :name external-ids :external-ids meta :meta context-id :context-id} :params}
        (create-context context-name external-ids meta :context-id context-id))
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
        (commit document :context-id context-id))
  (POST "/mcommit" {{documents :documents context-id :context-id} :params}
        (mcommit documents :context-id context-id))
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


;; TODO: rename from hirop-compojure.core to hirop-compojure.rpc
;; TODO: implement hirop-compojure.rest

;; The thing is that here we are using the session, but we probably shouldn't
;; We should have a database made of atoms (a map of atoms, indexed by context-id (or session cookie in case of stateful)
;; That database api should be specified as a protocol. Then we can make a memory-based (for embedded) and a Redis backend.

;; This would require to rewrite session.clj (renaming it to store.clj), avoid using ring-session at all (apart from ids / connection info).

;; Stateful should also be modified, too complex. It should probably rely on having a context-id uuid, like the REST.
;; Which should be used to fetch the record in the store database.

;; Expiral
;; http://kotka.de/blog/2010/03/memoize_done_right.html
;; https://github.com/clojure/core.cache

;; REST API
;; Remove create-context. Replace with external-ids and context name (mandatory) + meta (optional)
;; at every call (we can always write a stateful js library later).
;; A context that is not in memory (TODO: index by external ids and context name - in stateful the map is
;; used as the context-id) has to be queried,
;; otherwise we use the one in memory.
;; We keep clear-context(s) as courtesy urls, just to clean memory.

;; THE API:

;; Login
;; GET https://shirop.io/sessions/new
;; POST https://shirop.io/sessions
;; returns session-id

;; Context id
;; POST https://shirop.io/sessions/:session-id/contexts
;; with app-id, context-name, external-ids and meta JSON payload; returns context-id

;; DELETE https://shirop.io/sessions/:session-id/contexts

;; clear contexts
;; clear context
;; commit
;; push
;; pull
;; select
;; unselect

;; DELETE https://shirop.io/contexts/:context-id

;; PUT https://shirop.io/contexts/:context-id/:doc-id
;; POST https://shirop.io/contexts/:context-id/store
;; POST https://shirop.io/store/contexts/:context-id
;; PUT https://shirop.io/contexts/:context-id/selected/:selection-id/:doc-id
;; DELETE https://shirop.io/contexts/:context-id/selected/:selection-id
;; 
;; GET https://shirop.io/contexts/:context-id/external
;; GET https://shirop.io/contexts/:context-id/configurations
;; GET https://shirop.io/contexts/:context-id/configurations/:doctype
;; GET https://shirop.io/contexts/:context-id/doctypes/:doctype
;; GET https://shirop.io/contexts/:context-id/current/:id
;; GET https://shirop.io/contexts/:context-id/baseline/:id
;; GET https://shirop.io/contexts/:context-id/stored/:id
;; GET https://shirop.io/contexts/:context-id/history/:id
;; GET https://shirop.io/contexts/:context-id/conflicted
;; GET https://shirop.io/contexts/:context-id/documents/new
;; GET https://shirop.io/contexts/:context-id/documents/new/:doctype
;; GET https://shirop.io/contexts/:context-id/documents
;; GET https://shirop.io/contexts/:context-id/documents/:doctype
;; GET https://shirop.io/contexts/:context-id/selected/:selection-id
;; GET https://shirop.io/contexts/:context-id/selected/:selection-id/:doctype

;; all GET's have Expires: 0 or Cache-Control: no-cache in the header (except configurations, external-documents and doctype)

;; Use HTTP response codes, e.g. 409 Conflict, 404 Not Found, 403 Permission Denied
;; Use headers for return error messages, in Status Text or Warning header (actually not mandatory)

;; Actually, using a REST representation and a Redis backend, we may not need
;; the stateful stuff after all and the Redis atom thing; just assign Redis keys
;; with :context-id as a prefix and use the purely functional layer
;; (probably not directly, thin layer to only get the right portion of store
;; from Redis and not the whole store at every step).

;; The API moves away from the git semantics, but this could be easily recovered in the client libraries.
