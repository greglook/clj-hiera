clj-hiera
=========

Generates a graph of the dependency hierarchy of a set of clojure files. This
uses the [clojure.tools.namespace](https://github.com/clojure/tools.namespace)
library for namespace parsing and [Rhizome](https://github.com/ztellman/rhizome)
for graph generation using [Graphviz](http://www.graphviz.org/).

This tool was originally inspired by
[lein-ns-dep-graph](https://github.com/hilverd/lein-ns-dep-graph), but has many
additional options for graph generation.


## Usage

There are a few different ways to use clj-hiera. First, you'll need Graphviz
installed in order to generate the images. Check your local package manager:

```bash
# Debian/Ubuntu:
sudo apt-get install graphviz

# OS X with Homebrew:
brew install graphviz
```

### Deps Alias

If you're using `tools.deps`, you can use this by adding an alias to your
`deps.edn` file:

```clojure
:hiera
{:deps {io.github.greglook/clj-hiera {:git/tag "2.0.0", :git/sha "b14e514"}}
 :exec-fn hiera.main/graph
 :exec-args {,,,}}
```

To generate namespace graphs:

```bash
clj -X:hiera [opts]
```

### Clojure Tool

Alternately, you can use this as a standalone Clojure tool:

```bash
clj -Ttools install-latest :lib io.github.greglook/clj-hiera :as hiera
```

Then, to invoke the tool in any Clojure project:

```bash
clj -Thiera graph [opts]
```

### Leiningen

For Leiningen, you can use this via the `lein-hiera` plugin. Add the following
to your `project.clj` file or user-level profile:

```clojure
[lein-hiera "2.0.0"]
```

Then you can run:

```bash
lein hiera [opts]
```


## Options

The available options, and their default values are:

| name              | default                 | description |
|-------------------|-------------------------|-------------|
| `:sources`        | `#{"src"}`              | Set of directories containing source files to analyze.
| `:output`         | `"target/hiera"`        | Directory to write files to.
| `:layout`         | `:vertical`             | Whether to lay out the graph vertically or horizontally.
| `:cluster-depth`  | `0`                     | Number of namespace segments to cluster nodes by.
| `:external`       | `false`                 | Show external namespaces as nodes in the graph.
| `:ignore`         | `#{}`                   | A set of namespace prefixes or patterns to exclude from the graph.

When using Leiningen, graph generation options may be provided under the
`:hiera` key in the project map. You can also provide/override the
configuration options by specifying them on the command line with keyword
arguments:

```bash
lein hiera :cluster-depth 3 :layout :horizontal
```


## Example

This image shows the dependency hierarchy from a moderately complex project. The
namespaces are clustered by two levels, and it shows a dependency on the external
`puget` library.

![Example dependency hierarchy](doc/example.png)


## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
