(ns hiera.main
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.namespace.dependency :as ns-dep]
    [clojure.tools.namespace.file :as ns-file]
    [clojure.tools.namespace.find :as ns-find]
    [clojure.tools.namespace.track :as ns-track]
    [rhizome.dot :as dot]
    [rhizome.viz :as viz])
  (:import
    java.io.File
    java.util.regex.Pattern))


(def defaults
  "Default options for the tool."
  {:sources #{"src"}
   :output "target/hiera"
   :layout :vertical
   :cluster-depth 0
   :external false
   :ignore #{}})


;; ## Graph Generation

(defn- find-sources
  "Finds a list of source files located in the given directories."
  [dirs]
  (->>
    dirs
    (remove nil?)
    (map io/file)
    (mapcat file-seq)
    (filter (some-fn ns-file/clojurescript-file?
                     ns-file/clojure-file?))))


(defn- file-deps
  "Calculate the dependency graph of the namespaces in the given files."
  [files]
  (->>
    files
    (ns-file/add-files {})
    (::ns-track/deps)))


(defn- file-namespaces
  "Find the set of namespaces defined by the given files."
  [files]
  (into #{}
        (map (comp second ns-file/read-file-ns-decl))
        files))


(defn- match-ignore?
  "True if the given namespace matches the ignore pattern."
  [ns-str ignore]
  (if (instance? Pattern ignore)
    (re-matches ignore ns-str)
    (or (= ns-str (str ignore))
        (str/starts-with? ns-str (str ignore ".")))))


(defn- ignored-ns?
  "True if the namepace n should be ignored."
  [ignores n]
  (some (partial match-ignore? (str n)) ignores))


(defn- filter-ns
  "Filters namespaces based on the options."
  [data namespaces]
  (cond->> namespaces
    (not (:external data))
    (filter (:namespaces data))

    (seq (:ignore data))
    (remove (partial ignored-ns? (:ignore data)))))


(defn- graph-nodes
  "Generate a sequence of all nodes in the graph."
  [data]
  (let [dep-nodes (ns-dep/nodes (:graph data))
        internal-namespaces (:namespaces data)]
    (->>
      (concat internal-namespaces dep-nodes)
      (distinct)
      (filter-ns data))))


(defn- adjacent-to
  "Determine which nodes are adjacent to the given one in the graph."
  [data node]
  (->>
    node
    (ns-dep/immediate-dependencies (:graph data))
    (filter-ns data)))


(defn- node-cluster
  "Determine the name of the cluster the node belongs to."
  [data node]
  (let [depth (:cluster-depth data)]
    (when (pos? depth)
      (->
        (str node)
        (str/split #"\.")
        (as-> parts
          (take (min depth (dec (count parts))) parts)
          (str/join \. parts)
          (when-not (str/blank? parts)
            parts))))))


(defn- render-node
  "Render graph options for a node."
  [data node]
  (let [internal? (contains? (:namespaces data) node)
        cluster (node-cluster data node)]
    {:label (if (seq cluster)
              (subs (str node) (inc (count cluster)))
              (str node))
     :style (if internal? :solid :dashed)}))


;; ## Graph Writing

(defn- write-ns-graph
  "Write the namespace dot and image files."
  [data]
  (let [dot-str (dot/graph->dot
                  (graph-nodes data)
                  (partial adjacent-to data)
                  :vertical? (= :vertical (:layout data))
                  :node->descriptor (partial render-node data)
                  :node->cluster (partial node-cluster data)
                  :cluster->descriptor (fn [c] {:label c}))
        dot-file (io/file (:output data) "namespaces.dot")
        image-file (io/file (:output data) "namespaces.png")]
    (io/make-parents image-file)
    (spit dot-file dot-str)
    (viz/save-image (viz/dot->image dot-str) image-file)
    (println "Generated namespace graph at" (str image-file))))


(defn- write-cluster-graph
  "Write the cluster dot and image files."
  [data]
  ;; TODO: implement
  #_
  (let [dot-str "..."
        dot-file (io/file (:output data) "clusters.dot")
        image-file (io/file (:output data) "clusters.png")]
    (io/make-parents image-file)
    (spit dot-file dot-str)
    (viz/save-image (viz/dot->image dot-str) image-file)
    (println "Generated cluster graph at" (str image-file))))


;; ## Entry Points

(defn graph
  "Generate a dependency graph of the namespaces in the project using the given
  map of options."
  [opts]
  (let [opts (merge defaults opts)
        source-files (find-sources (:sources opts))
        data (assoc opts
                    :namespaces (file-namespaces source-files)
                    :graph (file-deps source-files))]
    (write-ns-graph data)
    (when (and (number? (:cluster-depth opts))
               (pos? (:cluster-depth opts)))
      (write-cluster-graph data))
    (flush)))


(defn -main
  "Main command-line entry point."
  [& args]
  (graph (apply hash-map (map read-string args)))
  (shutdown-agents)
  (System/exit 0))
