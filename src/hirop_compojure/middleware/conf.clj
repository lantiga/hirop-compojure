(ns hirop-compojure.middleware.conf
  (:use
   [hirop-compojure.rest :only [*get-hirop-conf*]]))

(defn wrap-hirop-conf [handler conf]
  (fn [request]
    (binding [*get-hirop-conf* (constantly conf)]
      (handler request))))

(defn wrap-hirop-conf* [handler f]
  (fn [request]
    (binding [*get-hirop-conf* f]
      (handler request))))
