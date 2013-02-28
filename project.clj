(defproject hirop-compojure "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [compojure "1.1.5"]
                 [ring-mock "0.1.3"]
                 [cheshire "4.0.2"]
                 [hirop "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[ring/ring-json "0.1.2"]]}})
