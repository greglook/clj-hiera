(ns hiera.main
  (:gen-class)
  (:require
    [clojure.java.io :as io]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.tools.namespace.dependency :as ns-dep]
    [clojure.tools.namespace.file :as ns-file]
    [clojure.tools.namespace.find :as ns-find]
    [clojure.tools.namespace.track :as ns-track]
    [rhizome.dot :as dot]
    [rhizome.viz :as viz])
  (:import
    java.io.File))


;; ## Command Options

(def cli-options
  "Command-line interface option specs."
  [["-o" "--output PATH" "Path to write the image file to"
    :default "target/ns-hierarchy.png"]
   [nil "--dot PATH" "Path to write the raw DOT graph to"]
   ["-l" "--layout LAYOUT" "Orientation to lay out the graph (horizontal or vertical)"
    :default :vertical
    :default-desc "vertical"
    :parse-fn keyword
    :validate [#{:horizontal :vertical} "Must be a valid layout orientation"]]
   [nil "--cluster-depth DEPTH" "Sets the number of namespace segments to cluster nodes by. Clusters must contain at least one fewer segment than the nodes themselves."
    :default 0
    :parse-fn parse-long
    :validate [nat-int? "Cluster depth must be non-negative integer"]]
   [nil "--ignore-ns NS" "A namespace prefix to exclude from the graph. May be provided multiple times."
    :multi true
    :default #{}
    :default-desc ""
    :update-fn conj]
   [nil "--show-external" "When set, the graph will include nodes for namespaces which are not defined in the source files, marked by a dashed border."
    :default false]
   ["-h" "--help" "Show command usage information"]])


(defn- usage-str
  "Return a string of usage information."
  [summary]
  (str "Usage: hiera [options] [source-path...]\n"
       "\n"
       "Generate a graph of the dependency hierarchy of the namespaces in the\n"
       "provided source paths.\n"
       "\n"
       "Options:\n"
       summary))


(defn- error-str
  "Return a string about some command errors."
  [errors]
  (str "There was an error with the provided options:\n\n"
       (str/join \newline errors)))


(defn- parse-args
  "Parse the command-line arguments into a map of options for the tool. May
  exit the JVM if the options specify stopping."
  [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      ;; User asked for help
      (:help options)
      (do
        (println (usage-str summary))
        (flush)
        (System/exit 0))

      ;; Some option parsing errors
      (seq errors)
      (binding [*out* *err*]
        (println (error-str errors))
        (flush)
        (System/exit 1))

      ;; Valid options.
      :else
      (let [paths (if (empty? arguments)
                    #{"src"}
                    (set arguments))]
        (assoc options :source-paths paths)))))


;; ## Graph Generation

;; Add two local functions until they are added to `clojure.tools.namespace`.
;; See: http://dev.clojure.org/jira/browse/TNS-35
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
    (file-seq)
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
    (flatten)))


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


(defn- ignored-ns?
  "True if the namepace n should be ignored."
  [prefixes n]
  (let [nstr (str n)]
    (not-any? #(str/starts-with? nstr (str %))
              prefixes)))


(defn- filter-ns
  "Filters namespaces based on the options."
  [context namespaces]
  (cond->> namespaces
    (not (:show-external context))
    (filter (:internal-ns context))

    (seq (:ignore-ns context))
    (filter (partial ignored-ns? (:ignore-ns context)))))


(defn- graph-nodes
  "Generate a sequence of all nodes in the graph."
  [context]
  (->>
    (:graph context)
    (ns-dep/nodes)
    (filter-ns context)))


(defn- adjacent-to
  "Determine which nodes are adjacent to the given one in the graph."
  [context node]
  (->>
    node
    (ns-dep/immediate-dependencies (:graph context))
    (filter-ns context)))


(defn- node-cluster
  "Determine the name of the cluster the node belongs to."
  [context node]
  (let [depth (:cluster-depth context)]
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
  [context node]
  (let [internal? (contains? (:internal-ns context) node)
        cluster (node-cluster context node)]
    {:label (if (seq cluster)
              (subs (str node) (inc (count cluster)))
              (str node))
     :style (if internal? :solid :dashed)}))



;; ## Entry Points

(defn hiera
  "Generate a dependency graph of the namespaces in the project."
  [opts]
  (let [source-files (find-sources (:source-paths opts))
        context (assoc opts
                       :internal-ns (file-namespaces source-files)
                       :graph (file-deps source-files))
        dot-graph (dot/graph->dot
                    (graph-nodes context)
                    (partial adjacent-to context)
                    :vertical? (= :vertical (:layout opts))
                    :node->descriptor (partial render-node context)
                    :node->cluster (partial node-cluster context)
                    :cluster->descriptor (fn [c] {:label c}))]
    (when-let [dot-path (:dot opts)]
      (io/make-parents dot-path)
      (spit dot-path dot-graph)
      (println "Wrote namespace dot graph to" dot-path))
    (when-let [image-path (:output opts)]
      (io/make-parents image-path)
      (viz/save-image (viz/dot->image dot-graph) image-path)
      (println "Generated namespace graph at" image-path))
    (flush)))


(defn -main
  "Main command-line entry point."
  [& args]
  (let [opts (parse-args args)]
    (hiera opts)
    (System/exit 0)))
