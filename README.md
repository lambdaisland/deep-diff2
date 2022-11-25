# lambdaisland/deep-diff2

<!-- badges -->
[![CircleCI](https://circleci.com/gh/lambdaisland/deep-diff2.svg?style=svg)](https://circleci.com/gh/lambdaisland/deep-diff2) [![cljdoc badge](https://cljdoc.org/badge/lambdaisland/deep-diff2)](https://cljdoc.org/d/lambdaisland/deep-diff2) [![Clojars Project](https://img.shields.io/clojars/v/lambdaisland/deep-diff2.svg)](https://clojars.org/lambdaisland/deep-diff2)
<!-- /badges -->

Recursively compare Clojure or ClojureScript data structures, and produce a colorized diff of the result.

![screenshot showing REPL example](screenshot.png)

Deep-diff2 is foremost intended for creating visual diffs for human consumption,
if you want to programatically diff/patch Clojure data structures then
[Editscript](https://github.com/juji-io/editscript) may be a better fit, see
[this write-up by Huahai Yang](https://juji.io/blog/comparing-clojure-diff-libraries/).

<!-- opencollective -->

&nbsp;

<img align="left" src="https://github.com/lambdaisland/open-source/raw/master/artwork/lighthouse_readme.png">

&nbsp;

## Support Lambda Island Open Source

deep-diff2 is part of a growing collection of quality Clojure libraries and
tools released on the Lambda Island label. If you are using this project
commercially then you are expected to pay it forward by
[becoming a backer on Open Collective](http://opencollective.com/lambda-island#section-contribute),
so that we may continue to enjoy a thriving Clojure ecosystem.

&nbsp;

&nbsp;

<!-- /opencollective -->

## Installation

deps.edn

```
lambdaisland/deep-diff2 {:mvn/version "2.7.169"}
```

project.clj

```
[lambdaisland/deep-diff2 "2.7.169"]
```

## Use

- [API docs](https://cljdoc.org/d/lambdaisland/deep-diff2/CURRENT)

``` clojure
(require '[lambdaisland.deep-diff2 :as ddiff])

(ddiff/pretty-print (ddiff/diff {:a 1 :b 2} {:a 1 :c 3}))
```

### Diffing

`lambdaisland.deep-diff2/diff` takes two arguments and returns a "diff", a data
structure that contains markers for insertions, deletions, or mismatches. These
are records with `-` and `+` fields.

``` clojure
(ddiff/diff {:a 1 :b 2} {:a 1 :b 3})
{:a 1, :b #lambdaisland.deep_diff.diff.Mismatch{:- 2, :+ 3}}
```

### Printing

You can pass this diff to `lambdaisland.deep-diff2/pretty-print`. This function
uses [Puget](https://github.com/greglook/puget) and
[Fipp](https://github.com/brandonbloom/fipp) to format the diff and print the
result to standard out.

For fine grained control you can create a custom Puget printer, and supply it to
`pretty-print`.

``` clojure
(def narrow-printer (ddiff/printer {:width 10}))

(ddiff/pretty-print (ddiff/diff {:a 1 :b 2} {:a 1 :b 3}) narrow-printer)
```

For more advanced uses like incorporating diffs into your own Fipp documents, see `lambdaisland.deep-diff2.printer/format-doc`, `lambdaisland.deep-diff2.printer/print-doc`.

### Print handlers for custom or built-in types

In recent versions deep-diff2 initializes its internal copy of Puget with
`{:print-fallback :print}`, meaning it will fall back to using the system
printer, which you can extend by extending the `print-method` multimethod.

This also means that we automatically pick up additional handlers installed by
libraries, such as [time-literals](https://github.com/henryw374/time-literals).

You can also register print handlers for deep-diff2 specifically by using
`lambdaisland.deep-diff2.printer-impl/register-print-handler!`, or by passing an
`:extra-handlers` map to `printer`.

If you are dealing with printing of custom types you might find that there are
multiple print implementations you need to keep up-to-date, see
[lambdaisland.data-printers](https://github.com/lambdaisland/data-printers) for
a high-level API that can work with all the commonly used print implementations.

#### Example of a custom type

See [repl_sessions/custom_type.clj](repl_sessions/custom_type.clj) for the full
code and results.

```clj
(deftype Degrees [amount unit]
  Object
  (equals [this that]
    (and (instance? Degrees that)
         (= amount (.-amount that))
         (= unit (.-unit that)))))

;; Using system handler fallback
(defmethod print-method Degrees [degrees out]
  (.write out (str (.-amount degrees) "°" (.-unit degrees))))
  
;; OR Using a Puget-specific handler
(lambdaisland.deep-diff2.printer-impl/register-print-handler!
 `Degrees
 (fn [printer value]
   [:span
    (lambdaisland.deep-diff2.puget.color/document printer :number (str (.-amount value)))
    (lambdaisland.deep-diff2.puget.color/document printer :tag "°")
    (lambdaisland.deep-diff2.puget.color/document printer :keyword (str (.-unit value)))]))
```

### Time, data literal

A common use case is diffing and printing Java date and time objects
(`java.util.Date`, `java.time.*`, `java.sql.Date|Time|DateTime`).

Chances are you already have print handlers (and data readers) set up for these
via the [time-literals](https://github.com/henryw374/time-literals) library
(perhaps indirectly by pulling in [tick](https://github.com/juxt/tick). In that
case these should _just work_.

```clj
(ddiff/diff #inst "2019-04-09T14:57:46.128-00:00"
            #inst "2019-04-10T14:57:46.128-00:00")
```
or
```clj
(import '[java.sql Timestamp])
(ddiff/diff (Timestamp. 0)
            (doto (Timestamp. 1000) (.setNanos 101)))
```

If you need to diff a rich set of time literal, using

```
(require '[time-literals.read-write])
(require '[lambdaisland.deep-diff2 :as ddiff])
(time-literals.read-write/print-time-literals-clj!)
(ddiff/pretty-print (ddiff/diff #time/date "2039-01-01" #time/date-time "2018-07-05T08:08:44.026"))
```

## Deep-diff 1 vs 2

The original deep-diff only worked on Clojure, not ClojureScript. In porting the
code to CLJC we were forced to make some breaking changes. To not break existing
consumers we decided to move both the namespaces and the released artifact to
new names, so the old and new deep-diff can exist side by side.

We also had to fork Puget to make it cljc compatible. This required breaking
changes as well, making it unlikely these changes will make it upstream, so
instead we vendor our own copy of Puget under `lambdaisland.deep-diff2.puget.*`.
This does mean we don't automatically pick up custom Puget print handlers,
unless they are *also* registered with our own copy of Puget. See above for more
info on that.

When starting new projects you should use `lambdaisland/deep-diff2`. However if
you have existing code that uses `lambdaisland/deep-diff` and you don't need the
ClojureScript support then it is not necessary to upgrade. The old version still
works fine (on Clojure).

You can upgrade of course, simply by replacing all namespace names from
`lambdaisland.deep-diff` to `lambdaisland.deep-diff2`. If you are only using the
top-level API (`diff`, `printer`, `pretty-print`) and you aren't using custom
print handlers, then things should work exactly the same. If you find that
deep-diff 2 behaves differently then please file an issue, you may have found a
regression.

The old code still lives on the `deep-diff-1` branch, and we do accept bugfix
patches there, so we may put out bugfix releases of the original deep-diff in
the future. When in doubt check the CHANGELOG.

<!-- contributing -->
## Contributing

Everyone has a right to submit patches to deep-diff2, and thus become a contributor.

Contributors MUST

- adhere to the [LambdaIsland Clojure Style Guide](https://nextjournal.com/lambdaisland/clojure-style-guide)
- write patches that solve a problem. Start by stating the problem, then supply a minimal solution. `*`
- agree to license their contributions as EPL 1.0.
- not break the contract with downstream consumers. `**`
- not break the tests.

Contributors SHOULD

- update the CHANGELOG and README.
- add tests for new functionality.

If you submit a pull request that adheres to these rules, then it will almost
certainly be merged immediately. However some things may require more
consideration. If you add new dependencies, or significantly increase the API
surface, then we need to decide if these changes are in line with the project's
goals. In this case you can start by [writing a pitch](https://nextjournal.com/lambdaisland/pitch-template),
and collecting feedback on it.

`*` This goes for features too, a feature needs to solve a problem. State the problem it solves, then supply a minimal solution.

`**` As long as this project has not seen a public release (i.e. is not on Clojars)
we may still consider making breaking changes, if there is consensus that the
changes are justified.
<!-- /contributing -->

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

<!-- license -->
## License

Copyright &copy; 2018-2020 Arne Brasseur and contributors

Available under the terms of the Eclipse Public License 1.0, see LICENSE.txt
<!-- /license -->
