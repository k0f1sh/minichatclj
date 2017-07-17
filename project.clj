(defproject minichat "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.6.0"]
                 [com.taoensso/sente "1.11.0"]
                 [http-kit "2.3.0-alpha2"]
                 [ring "1.6.1"]
                 [org.clojure/clojurescript "1.9.521"]
                 [org.clojure/core.async "0.3.443"]
                 [hiccup "1.0.5"]
                 [reagent "0.7.0"]]
  :plugins [[lein-cljsbuild "1.1.6"]]
  :cljsbuild {:builds [{:source-paths ["src-cljs"]
                        :compiler {:output-to "resources/public/js/main.js"
                                   :optimizations :advanced
                                   :pretty-print false}}]}
  :main minichat.core)

