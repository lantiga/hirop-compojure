(ns hirop-compojure.rest-test
  (:use clojure.test
        ring.middleware.json
        ring.mock.request
        [hirop.core :only [hid checkout-selected get-selected-ids get-document]]
        hirop.protocols
        hirop.backend
        hirop.atom-store
        hirop-compojure.middleware.store
        hirop-compojure.middleware.conf
        hirop-compojure.rest
        [compojure.core :only [routes]])
  (:require [cheshire.core :as json]
            [compojure.handler :refer [api]]))

(defn- json-request 
  [method uri params]
  (->
    (request method uri)
    (body (json/generate-string params))
    (content-type "application/json")))

(def doctypes
  {:Foo {:fields {:id {}}}
   :Bar {:fields {:title {}}}
   :Baz {:fields {:title {}}}
   :Baq {:fields {:title {}}}})

(def context
  {:relations
   [{:from :Bar :to :Foo :external true :cardinality :one}
    {:from :Baz :to :Bar :cardinality :many}]
   :prototypes
   {:Zen [:Bar :Baz]}
   :selections
   {:test
    {:Foo {:sort-by [:id] :select :last}
     :Bar {:select :all}
     :Baz {:select :all}}}
   :configurations {}})

(def docs
  {"doc0"
   {:_hirop {:id "doc0" :type "Foo"} :id "doc0"}
   "doc1"
   {:_hirop {:id "doc1" :type "Bar" :rels {:Foo "doc0"}} :title "First"}
   "doc2"
   {:_hirop {:id "doc2" :type "Bar" :rels {:Foo "doc0"}} :title "Second"}
   "doc3"
   {:_hirop {:id "doc3" :type "Baz" :rels {:Bar ["doc1" "doc2"]}} :title "Third"}})

(def modified-doc
  {:_hirop {:id "doc3" :type "Baz" :rels {:Bar ["doc1" "doc2"]}} :title "Third*"})

(defmethod fetch :test
  [backend context]
  {:documents (vec (vals docs))})

(defmethod save :test
  [backend context]
  {:result :success})

(def test-conf
  {:doctypes doctypes
   :contexts {:TestContext context}
   :meta {}
   :backend {:name :test}})

(def store (atom-store))

(def hirop-handler
  (->
   (routes
    hirop-routes
    commands-routes)
   (wrap-hirop-conf test-conf)
   (wrap-hirop-store store)
   api
   wrap-json-params))

(defn- context-url
  [context-id url]
  (str "/contexts/" context-id "/" url))

(deftest rest-test
  (let [resp (hirop-handler (json-request :post "/contexts" {:context-name "TestContext" :external-ids {:Foo "doc0"}}))
        ctx-id (get-in resp [:body :context-id])
        ctx (get-context store ctx-id)]
    (is (= (:doctypes ctx) doctypes))
    (is (= (:relations ctx) (:relations context)))
    (let [resp (hirop-handler (request :post (context-url ctx-id "pull")))
          ctx (get-context store ctx-id)]
      (is (= (set (vals (:stored ctx))) (set (vals docs))))

      (let [docs-resp (hirop-handler (request :get (context-url ctx-id "documents")))]
        (is (= (set (vals docs)) (set (:body docs-resp)))))

      (let [req (json-request :post (context-url ctx-id "documents") {:document modified-doc})
            resp (hirop-handler req)
            ctx (get-context store ctx-id)]
        (is (= (get-in ctx [:baseline (hid modified-doc)])
               (get docs (hid modified-doc))))
        (is (= (get-in ctx [:stored (hid modified-doc)])
               (get docs (hid modified-doc))))
        (is (= (update-in (get-in ctx [:starred (hid modified-doc)]) [:_hirop] dissoc :meta)
               modified-doc))

        (let [req (request :post (context-url ctx-id "selected/test/"))
              resp (hirop-handler req)
              req (request :get (context-url ctx-id "selected/test/documents"))
              resp (hirop-handler req)]
          (is (= (set (get-in resp [:body :Bar])) (set (vals (select-keys docs ["doc1" "doc2"])))))

          (let [req (request :delete (context-url ctx-id "selected/test/Bar"))
                resp (hirop-handler req)
                req (request :get (context-url ctx-id "selected/test/documents/Bar"))
                resp (hirop-handler req)]
            (is (empty? (:body resp)))))))))

(deftest commands-test
  (let [resp (hirop-handler (json-request :post "/contexts/commands"
                                          {:context-name "TestContext"
                                           :external-ids {:Foo "doc0"}
                                           :commands
                                           [["post" "/pull" {}]
                                            ["get" "/doctypes" {}]
                                            ["get" "/documents" {}]]}))]
    (is (= (get-in resp [:body :responses 0]) {:result :success}))
    (is (= (set (keys (get-in resp [:body :responses 1]))) (set (keys doctypes))))
    (is (= (set (get-in resp [:body :responses 2])) (set (vals docs))))))

(comment (run-tests *ns*))
