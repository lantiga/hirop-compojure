(ns hirop-compojure.middleware.vars
  (:use
   [hirop-compojure.rest :only [*contexts* *doctypes* *meta* *backend*]]
   [hirop.backend :only [*connection-data*]]))

(defn wrap-hirop-vars [handler {:keys [contexts doctypes meta backend]}]
  (fn [request]
    (binding [*contexts* contexts
              *doctypes* doctypes
              *meta* meta
              *backend* backend
              *connection-data* backend]
      (handler request))))

(defn wrap-hirop-session-vars [handler]
  (fn [request]
    (binding [*contexts* (get-in request [:session :hirop :contexts])
              *doctypes* (get-in request [:session :hirop :doctypes])
              *meta* (get-in request [:session :hirop :meta])
              *backend* (get-in request [:session :hirop :backend])
              *connection-data* (get-in request [:session :hirop :backend])]
      (handler request))))

