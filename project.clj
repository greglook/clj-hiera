(defproject lein-hiera "0.1.0-SNAPSHOT"
  :description "Generates a dependency hierarchy graph for project namespaces."
  :url "https://github.com/greglook/lein-hiera"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}
  :eval-in-leiningen true

  :dependencies
  [[org.clojure/tools.namespace "0.2.4"]
   [rhizome "0.2.0"]])
