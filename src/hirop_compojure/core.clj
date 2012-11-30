(ns hirop-compojure.core
  (:use compojure.core
        clojure.set
        [ring.util.response :only [response redirect status]])
  (:require [hirop.core :as hirop]
            [hirop.backend :as backend]
            [hirop.session :as session]
            [cheshire.core :as json]
            [ring.util.response :as response]))

;; TODO: move to session-based frontend namespace
(defn change-selection [selection-id session-updater]
  (let [old-selection (get-in (session/get :store) [:selections (keyword selection-id)])
        session (session/update! session-updater)
        new-selection (get-in session [:store :selections (keyword selection-id)])
        changed-selection (set (map first (filter (fn [[k v]] (not= v (get old-selection k))) new-selection)))
        removed (set (map first (filter (fn [[k _]] (not (contains? new-selection k))) old-selection)))]
    (union changed-selection removed)))

;; TODO: move to session-based frontend namespace
(defn init [{:keys [backend doctypes contexts]}]
  (session/clear!)
  (session/put! :backend backend)
  (session/put! :doctypes doctypes)
  (session/put! :contexts contexts))

;; TODO: basically all the handlers should be moved to a session-based frontend namespace
;; The fact that the routes go through compojure is a detail

;; TODO: extend to multiple concurrent contexts

;; TODO: make a couch embedded backend

(def vec-response
  (comp response vec))

(defroutes hirop-routes
  (GET "/doctypes" []
       (response (session/get :doctypes)))
  (GET "/contexts" []
       (response (session/get :contexts)))
  (POST "/clean-context" {}
        (session/remove-keys! [:store :context]))
  (POST "/init-context" {{context-name :name external-ids :external-ids} :params}
        (let [contexts (session/get :contexts)
              doctypes (session/get :doctypes)
              loggedin (session/get :loggedin)
              context-name (keyword context-name)
              context (get contexts context-name)
              configurations (get-in contexts [context-name :configurations])
              context (hirop/init-context context-name context doctypes configurations external-ids)
              store (hirop/new-store context-name {:username loggedin})]
          (if (get contexts context-name)
            (session/update!
             (fn [session]
               (-> session
                   (assoc :context context)
                   (assoc :store store))))
            (-> (response "No context found") (status 500)))))
  ;; TODO: if selection-id in params, then fill document with currently selected relations
  (GET "/new-document" {{doctype :doctype} :params}
       (let [{context :context store :store} (session/update-in! [:store] hirop/inc-uuid)
             new-id (hirop/get-uuid store)
             document (hirop/new-document context (keyword doctype))
             document (assoc document :_id new-id :_rev (hirop/zero-rev))]
         (response document)))
  ;; TODO: if selection-id in params, then fill documents with currently selected relations
  (GET "/new-documents" {{doctype-map :doctype-map} :params}
       (let [document-map
             (reduce
              (fn [res [doctype n]]
                (assoc res doctype
                       (reduce
                        (fn [res _]
                          (let [{store :store context :context} (session/update-in! [:store] hirop/inc-uuid)
                                new-id (hirop/get-uuid store)
                                document (hirop/new-document context (keyword doctype))
                                document (assoc document :_id new-id :_rev (hirop/zero-rev))]
                            (conj res document)))
                        []
                        (repeat (read-string n) nil))))
              {}
              doctype-map)]
         (response document-map)))
  (GET "/get-document" {{id :id} :params}
       (response (hirop/get-document (session/get :store) id)))
  (GET "/get-external-documents" {params :params}
       (vec-response
         (map (fn [[_ id]] (hirop/get-document (session/get :store) id)) (session/get [:context :external-ids]))))
  (GET "/get-configurations" {params :params}
       (response (session/get-in [:context :configurations])))
  (GET "/get-configuration" {{doctype :doctype} :params}
       (response
        (let [doctype (keyword doctype)]
          (session/get-in [:context :configurations doctype]))))
  (GET "/get-doctype" {{doctype :doctype} :params}
       (response
        (hirop/get-doctype (session/get :context) (keyword doctype))))
  (GET "/get-baseline" {{id :id} :params}
       (response
        (hirop/get-baseline (session/get :store) id)))
  (POST "/commit" {{document :document} :params}
        (session/update-in! [:store] hirop/commit document))
  (POST "/mcommit" {{documents :documents} :params}
        (session/update-in! [:store] hirop/mcommit documents))
  (POST "/pull" {params :params}
        (do
          ;; TODO: here we should really query outside the transaction and then merge in the 
          ;; update (fetch might take a long time especially if db is remote)
          (session/update!
           (fn [session]
             (update-in session [:store] hirop/pull (:context session) (partial backend/fetch (session/get :backend)))))
          (response
           {:result (if (hirop/any-conflicted? (session/get :store)) :conflict :success)})))
  (GET "/get-conflicted-ids" {params :params}
       (vec-response
        (hirop/get-conflicted-ids (session/get :store))))
  (GET "/any-conflicted" {params :params}
       (response
        (str (hirop/any-conflicted? (session/get :store)))))
  (GET "/checkout-conflicted" {params :params}
       (response
        (hirop/checkout-conflicted (session/get :store))))
  (POST "/push" {params :params}
        (do
          ;; TODO: see comments for pull, same thing. Save might take a long time (it might even throw an exception).
          (let [store (session/get :store)
                save-info (hirop/push-save store (partial backend/save (session/get :backend)))]
            ;; With this version, save is not executed in an atom and only the data that has been saved is unstarred. 
            ;; Not sure what about re-starring, think about it (TODO).
            (session/update-in! [:store] hirop/push-post-save save-info))
          ;; With this version, save is executed in atom swap!, which might be retried
          ;;(update-in-session! [:store] hirop/push backend/save)
          (response
           {:result (hirop/get-push-result (session/get :store))})))
  (POST "/save" {{documents :documents} :params}
        (do
          (let [store (session/update-in! [:store] (hirop/mcommit documents)) 
                save-info (hirop/push-save store (partial backend/save (session/get :backend)))]
            (session/update-in! [:store] hirop/push-post-save save-info))
          (response
           {:result (hirop/get-push-result (session/get :store))})))
  (GET "/history" {{id :id} :params}
       (vec-response
        (backend/history (session/get :backend) id)))
  (GET "/checkout" {{doctype :doctype} :params}
       (if doctype
         (vec-response
          (hirop/checkout (session/get :store) doctype))
         (hirop/checkout (session/get :store))))
  (GET "/get-selected-ids" {{selection-id :selection-id doctype :doctype} :params}
       (if doctype
         (vec-response
          (hirop/get-selected-ids (session/get :store) (keyword selection-id) (keyword doctype)))
         (hirop/get-selected-ids (session/get :store) (keyword selection-id))))
  (GET "/checkout-selected" {{selection-id :selection-id doctype :doctype} :params}
       (if doctype
         (vec-response
          (hirop/checkout-selected (session/get :store) (keyword selection-id) (keyword doctype)))
         (response (hirop/checkout-selected (session/get :store) (keyword selection-id)))))
  (POST "/select" {{id :id selection-id :selection-id} :params}
        (vec-response
         (change-selection
          selection-id
          (fn [session]
            (update-in session [:store]
                       (fn [store]
                         (if id
                           (hirop/select-document store (:context session) id (keyword selection-id))
                           (hirop/select-defaults store (:context session) (keyword selection-id)))))))))
  (POST "/unselect" {{doctype :doctype selection-id :selection-id} :params}
        (vec-response
         (change-selection
          selection-id
          (fn [session]
            (update-in session [:store] #(hirop/unselect % (:context session) (keyword selection-id) (keyword doctype))))))))


(defmacro hirop-routes-with-context
  [prefix]
  `(context ~prefix [] hirop-routes))

