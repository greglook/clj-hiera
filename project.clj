(defproject lein-depnet "0.1.0"
  :description "Generates a network dependency graph for project namespaces."
  :url "https://github.com/greglook/lein-depnet"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}
  :eval-in-leiningen true
  :dependencies
  [[org.clojure/tools.namespace "0.2.4"]
   [rhizome "0.2.0"]])
