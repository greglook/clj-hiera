(ns leiningen.hiera)


(defn hiera
  "Generate a dependency graph of the namespaces in the project. The options
  can be provided by a map under `:hiera` in the project definition, or as
  keyword option pairs on the command-line.

  Example:

      lein hiera :cluster-depth 2 :layout :horizontal"
  [project & args]
  (require 'hiera.main)
  (hiera.main/graph
    (apply assoc
           (:hiera project)
           (map read-string args))))
