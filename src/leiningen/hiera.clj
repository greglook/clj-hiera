(ns leiningen.hiera
  (:require
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.namespace.dependency :as ns-dep]
    [clojure.tools.namespace.file :as ns-file]
    [clojure.tools.namespace.find :as ns-find]
    [clojure.tools.namespace.track :as ns-track])
  (:import
    java.io.File))


(def default-options
  {:path "target/ns-hierarchy.png"
   :vertical true
   :show-external false
   :cluster-depth 0
   :trim-ns-prefix true
   :ignore-ns #{}})


;; Add two local functions until they are added to `clojure.tools.namespace`.
;; See: http://dev.clojure.org/jira/browse/TNS-29?focusedCommentId=36741#comment-36741
(defn- clojurescript-file?
  "Returns true if the file represents a normal ClojureScript source file."
  [^File file]
  (and (.isFile file)
       (.endsWith (.getName file) ".cljs")))


(defn- find-sources-in-dir
  "Searches recursively under dir for source files (.clj and .cljs).
  Returns a sequence of File objects, in breadth-first sort order."
  [dir]
  (->>
    (io/file dir)
    file-seq
    (filter #(or (clojurescript-file? %)
                 (ns-file/clojure-file? %)))
    (sort-by #(.getAbsolutePath ^File %))))


(defn- find-sources
  "Finds a list of source files located in the given directories."
  [dirs]
  (->>
    dirs
    (filter identity)
    (map find-sources-in-dir)
    flatten))


(defn- file-deps
  "Calculates the dependency graph of the namespaces in the given files."
  [files]
  (->>
    files
    (ns-file/add-files {})
    ::ns-track/deps))


(defn- file-namespaces
  "Calculates the namespaces defined by the given files."
  [files]
  (map (comp second ns-file/read-file-ns-decl) files))


(defn- ignored-ns?
  [context n]
  (not (some #(.startsWith (str n) (str %))
             (:ignore-ns context))))


(defn- filter-ns
  "Filters namespaces based on the context options."
  [context namespaces]
  (cond->> namespaces
    (not (:show-external? context (:show-external context))) ; TODO: deprecate :show-external?
    (filter (:internal-ns context))

    (:ignore-ns context)
    (filter (partial ignored-ns? context))))


(defn- graph-nodes
  [context]
  (->>
    (:graph context)
    ns-dep/nodes
    (filter-ns context)))


(defn- adjacent-to
  [context node]
  (->>
    node
    (ns-dep/immediate-dependencies (:graph context))
    (filter-ns context)))


(defn- node-cluster
  [context node]
  (let [depth (:cluster-depth context)]
    (when (< 0 depth)
      (->
        (str node)
        (str/split #"\.")
        (as-> parts
          (take (min depth (dec (count parts))) parts)
          (str/join \. parts)
          (when-not (empty? parts) parts))))))


(defn- render-node
  [context node]
  (let [internal? (contains? (:internal-ns context) node)
        cluster (node-cluster context node)]
    {:label (if (and (:trim-ns-prefix context)
                     (not (empty? cluster)))
              (subs (str node) (inc (count cluster)))
              (str node))
     :style (if internal? :solid :dashed)}))


(defn- parse-args
  "Parse the provided command-line options, returning a map of keyword options
  followed by a sequence of source paths."
  [args]
  (loop [opts {}
         args args]
    (if (and (<= 2 (count args)) (= \: (ffirst args)))
      (let [[k v & more] args
            k (keyword (subs k 1))]
        (case k
          (:dot :path)
          (recur (assoc opts k v) more)

          (:vertical :show-external :cluster-depth :trim-ns-prefix)
          (recur (assoc opts k (read-string v)) more)

          :ignore-ns
          (recur (update opts k (fnil conj #{}) v) more)

          (throw (IllegalArgumentException.
                   (str "Unknown hiera option: " k)))))
      [opts args])))


(defn hiera
  "Generate a dependency graph of the namespaces in the project. Accepts keyword options and an optional list of additional source paths to search.

  Options may include:

  - :dot             If set, save the raw DOT graph to this path.
  - :path            The location to output the graph image to.
  - :vertical        Specifies whether to lay out the graph vertically or horizontally.
  - :show-external   When set, the graph will include nodes for namespaces which are not defined in the source files, marked by a dashed border.
  - :cluster-depth   Sets the number of namespace segments to cluster nodes by. Clusters must contain at least one fewer segment than the nodes themselves.
  - :trim-ns-prefix  When set, clustered namespaces will have the cluster prefix removed from the node labels.
  - :ignore-ns       A namespace prefix to exclude from the graph. May be provided multiple times.

  Example usage:

      lein hiera :cluster-depth 2 ../project/src"
  [project & args]
  (require 'rhizome.dot 'rhizome.viz)
  (let [[opts source-paths] (parse-args args)
        source-files (find-sources (into (set (:source-paths project)) source-paths))
        context (merge default-options
                       (:hiera project)
                       opts
                       {:internal-ns (set (file-namespaces source-files))
                        :graph (file-deps source-files)})
        graph->dot (ns-resolve 'rhizome.dot 'graph->dot)
        dot->image (ns-resolve 'rhizome.viz 'dot->image)
        save-image (ns-resolve 'rhizome.viz 'save-image)
        dot-graph (graph->dot
                    (graph-nodes context)
                    (partial adjacent-to context)
                    :vertical? (:vertical context)
                    :node->descriptor (partial render-node context)
                    :node->cluster (partial node-cluster context)
                    :cluster->descriptor (fn [c] {:label c}))]
    (when-let [dot-path (:dot context)]
      (io/make-parents dot-path)
      (spit dot-path dot-graph)
      (println "Wrote namespace dot graph to" dot-path))
    (when-let [image-path (:path context)]
      (io/make-parents image-path)
      (save-image (dot->image dot-graph) image-path)
      (println "Generated namespace graph at" image-path))))
