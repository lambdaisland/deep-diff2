(ns lambdaisland.deep-diff2
  (:require [lambdaisland.deep-diff2.diff-impl :as diff-impl]
            [lambdaisland.deep-diff2.printer-impl :as printer-impl]))

(defn diff
  "Compare two values recursively.

  The result is a data structure similar to the ones passed in, but with
  Insertion, Deletion, and Mismatch objects to mark differences.

  When two collections are considered to be in the same type class then their
  contents are compared.

  Vectors, sequences, arrays and Java lists are all considered a single type
  class, as are Clojure and Java maps.

  Insertions/Deletions in maps are marked by wrapping the key, even though the
  change applies to the whole map entry."
  [expected actual]
  (diff/diff expected actual))

(defn printer
  "Construct a Puget printer instance suitable for printing diffs.

  Extra options are passed on to Puget. Extra type handlers can be provides as
  `:extra-handlers` (a map from symbol to function), or by
  using [[lambdaisland.deep-diff.printer/register-print-handler!]]"
  ([]
   (printer {}))
  ([opts]
   (printer-impl/puget-printer opts)))

(defn pretty-print
  "Pretty print a diff.

  Pretty print a diffed data structure, as obtained from [[diff]]. Optionally
  takes a Puget printer instance, see [[printer]]."
  ([diff]
   (pretty-print diff (build-printer)))
  ([diff printer]
   (-> diff
       (printer-impl/format-doc printer)
       (printer-impl/print-doc printer))))
