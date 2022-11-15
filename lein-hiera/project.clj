(defproject lein-hiera "2.0.0"
  :description "Generates a dependency hierarchy graph for project namespaces."
  :url "https://github.com/greglook/clj-hiera"
  :license {:name "Public Domain"
            :url "http://unlicense.org/"}

  :eval-in-leiningen true
  :deploy-branches ["main"]
  :source-paths ["src" "../src"]

  :dependencies
  [[org.clojure/tools.namespace "1.3.0"]
   [rhizome "0.2.9"]])
