(ns hirop-compojure.middleware.backend
  (:use [hirop.backend :only [*connection-data*]]))

(defn wrap-hirop-backend [handler]
  (fn [request]
    (binding [*connection-data* (get-in request [:session :hirop :connection-data])]
      (handler request))))
