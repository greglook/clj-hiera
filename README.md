# lein-depnet

Generates a graph of the dependency network of a set of clojure files. This uses
the [clojure.tools.namespace](https://github.com/clojure/tools.namespace)
library for namespace parsing and [Rhizome](https://github.com/ztellman/rhizome)
for graph generation using [Graphviz](http://www.graphviz.org/).

This plugin is inspired by
[lein-ns-dep-graph](https://github.com/hilverd/lein-ns-dep-graph), but with
additional options for graph generation.

## Usage

Add `[lein-depnet "0.1.0"]` into the `:plugins` vector of your
`:user` profile.

    $ lein depnet

This will generate a dependency graph in `target/dependencies.png`, showing the
interdependency of the project's source namespaces. By default, all directories
in the project's `:source-paths` are included. Additional directories to include
may be given as command-line arguments:

    $ lein depnet ../foo-lib/src ../bar-lib/src

## Options

Graph generation may be controlled with additional options under the `:depnet`
key in the project map. Common options should be placed in the `user` profile.

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
