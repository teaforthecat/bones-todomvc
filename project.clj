(defproject todomvc-re-frame "0.9.0"
  :dependencies [[org.clojure/clojure        "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [reagent "0.6.0-rc"]
                 [re-frame "0.9.0"]
                 [secretary "1.2.3"]

                 ;; [bones/editable "0.1.2"]

                 ]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-figwheel "0.5.6"]]

  :hooks [leiningen.cljsbuild]

  :profiles {:dev  {:dependencies [[binaryage/devtools "0.8.3"]
                                   [figwheel-sidecar "0.5.6"]
                                   [com.cemerick/piggieback "0.2.1"]
                                   ]
                    :cljsbuild
                    {:builds {:client {:compiler {:asset-path           "js"
                                                  :optimizations        :none
                                                  :source-map           true
                                                  :source-map-timestamp true
                                                  :main                 "todomvc.core"}
                                       :figwheel {:on-jsload "todomvc.core/main"}}}}}

             :prod {:cljsbuild
                    {:builds {:client {:compiler {:optimizations :advanced
                                                  :elide-asserts true
                                                  :pretty-print  false}}}}}}

  :figwheel {:server-port 3450
             :nrepl-port  3500
             :repl        true}


  :clean-targets ^{:protect false} ["resources/public/js" "target"]

  :cljsbuild {:builds {:client {:source-paths ["src" "../../src"]
                                :compiler     {:output-dir "resources/public/js"
                                               :output-to  "resources/public/js/client.js"}}}})
