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
   [rhizome.viz :as rhizome]
   [rhizome.dot :as rdot])
  (:import
   java.io.File))

(def default-options
  {:path "target/ns-hierarchy.png"
   :vertical false
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

(defn- save-dot
  [string filename]
  (with-open [wrtr (io/writer filename)]
    (.write wrtr string))
  (println "Generated namespace graph in" filename))

(defn hiera
  "Generate a dependency graph of the namespaces in the project."
  [project & args]
  (let [source-files (find-sources (concat (:source-paths project) args))
        context (merge default-options
                       (:hiera project)
                       {:internal-ns (set (file-namespaces source-files))
                        :graph (file-deps source-files)})
        conf {:vertical? (:vertical context)
              :node->descriptor (partial render-node context)
              :node->cluster (partial node-cluster context)
              :cluster->descriptor (fn [c] {:label c})}]
    (apply (partial rhizome/save-graph
                    (graph-nodes context)
                    (partial adjacent-to context))
           (flatten (vec (assoc conf :filename (:path context)))))
    (println "Generated namespace graph in" (:path context))
    (save-dot (apply (partial rdot/graph->dot
                              (graph-nodes context)
                              (partial adjacent-to context))
                     (flatten (vec conf)))
              (str/replace (:path context) #".png" ".dot"))))
