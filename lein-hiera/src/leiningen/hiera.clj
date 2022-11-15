(ns leiningen.hiera)


(defn hiera
  "Generate a dependency graph of the namespaces in the project. The options
  can be provided by a map under `:hiera` in the project definition, or as
  keyword option pairs on the command-line.

  Example:

      lein hiera :cluster-depth 2 :layout :horizontal"
  [project & args]
  (let [graph (requiring-resolve 'hiera.main/graph)
        opts (merge {:sources (set (:source-paths project))}
                    (:hiera project)
                    (apply array-map (map read-string args)))]
    (graph opts)))
