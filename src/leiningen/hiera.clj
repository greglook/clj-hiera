(ns leiningen.hiera
  (:require
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    (clojure.tools.namespace
      [dependency :as ns-dep]
      [file :as ns-file]
      [find :as ns-find]
      [track :as ns-track])
    [rhizome.viz :as rhizome]))


(def default-options
  {:path "target/dependencies.png"
   :vertical? true
   :show-external? false
   :cluster-depth 0
   :trim-ns-prefix true
   :ignore-ns #{}})


(defn- find-sources
  "Finds a list of source files located in the given directories."
  [dirs]
  (->>
    dirs
    (filter identity)
    (map (comp ns-find/find-clojure-sources-in-dir io/file))
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
    (not (:show-external? context)) (filter (:internal-ns context))
    (:ignore-ns context) (filter (partial ignored-ns? context))))


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
          (str/join \. parts))))))


(defn- render-node
  [context node]
  (let [internal? (contains? (:internal-ns context) node)
        cluster (node-cluster context node)]
    {:label (if (and (:trim-ns-prefix context)
                     (not (empty? cluster)))
              (subs (str node) (inc (count cluster)))
              (str node))
     :style (if internal? :solid :dashed)}))


(defn hiera
  "Generate a dependency graph of the namespaces in the project."
  [project & args]
  (let [source-files (find-sources (concat (:source-paths project) args))
        context (merge default-options
                       (:hiera project)
                       {:internal-ns (set (file-namespaces source-files))
                        :graph (file-deps source-files)})]
    (rhizome/save-graph
      (graph-nodes context)
      (partial adjacent-to context)
      :vertical? (:vertical? context)
      :node->descriptor (partial render-node context)
      :node->cluster (partial node-cluster context)
      :cluster->descriptor (fn [c] {:label c})
      :filename (:path context))))
