# lambdaisland/deep-diff

Recursively compare Clojure data structures, and produce a colorized diff of the result.

![screenshot showing REPL example](screenshot.png)

## Install

[![Clojars Project](https://img.shields.io/clojars/v/lambdaisland/deep-diff.svg)](https://clojars.org/lambdaisland/deep-diff)

## Use

- [API docs](https://cljdoc.org/d/lambdaisland/deep-diff/CURRENT)

``` clojure
(require '[lambdaisland.deep-diff :as ddiff])

(pretty-print (diff {:a 1 :b 2} {:a 1 :c 3}))
```

### Diffing

`lambdaisland.deep-diff/diff` takes two arguments and returns a "diff", a data
structure that contains markers for insertions, deletions, or mismatches. These
are records with `-` and `+` fields.

``` clojure
(diff {:a 1 :b 2} {:a 1 :b 3})
{:a 1, :b #lambdaisland.deep_diff.diff.Mismatch{:- 2, :+ 3}}
```

### Printing

You can pass this diff to `lambdaisland.deep-diff/pretty-print`. This function
uses [Puget](https://github.com/greglook/puget) and
[Fipp](https://github.com/brandonbloom/fipp) to format the diff and print the
result to standard out.

For fine grained control you can create a custom Puget printer, and supply it to
`pretty-print`.

``` clojure
(def narrow-printer (lambdaisland.deep-diff/printer {:width 10}))

(pretty-print (diff {:a 1 :b 2} {:a 1 :b 3}) narrow-printer)
```

For more advanced uses like incorporating diffs into your own Fipp documents, see `lambdaisland.deep-diff.printer/format-doc`, `lambdaisland.deep-diff.printer/print-doc`.

You can register print handlers for new types using
`lambdaisland.deep-diff.printer/register-print-handler!`, or by passing and
`:extra-handlers` map to `printer`.

## Credits

This library builds upon
[clj-diff](https://github.com/brentonashworth/clj-diff), which implements a
diffing algorithm for sequences, and
[clj-arrangements](https://github.com/greglook/clj-arrangement), which makes
disparate types sortable.

Pretty printing and colorization are handled by
[Puget](https://github.com/greglook/puget) and
[Fipp](https://github.com/brandonbloom/fipp).

This library was originally developed as part of the
[Kaocha](https://github.com/lambdaisland/kaocha) test runner.

Another library that implements a form of data structure diffing is [editscript](https://github.com/juji-io/editscript).

## License

Copyright &copy; 2018 Arne Brasseur

Available under the terms of the Eclipse Public License 1.0, see LICENSE.txt
