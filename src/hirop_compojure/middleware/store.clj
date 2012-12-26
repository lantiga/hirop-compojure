(ns hirop-compojure.middleware.store)

(defn wrap-hirop-store [handler store]
  (fn [request]
    (let [request (assoc-in request [:hirop :store] store)]
      (handler request))))
