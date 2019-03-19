# Unreleased

## Fixed

- Fix issue [diff hangs on nil](https://github.com/lambdaisland/deep-diff/issues/6) (see [upstream issue](https://github.com/droitfintech/clj-diff/issues/3))

## Changed

- mvxcvi/puget {:mvn/version "1.0.3"} -> {:mvn/version "1.1.1"}
- fipp {:mvn/version "0.6.14"} -> {:mvn/version "0.6.17"}
- org.clojure/core.rrb-vector {:mvn/version "0.0.13"} -> {:mvn/version "0.0.14"}
- tech.droit/clj-diff {:mvn/version "1.0.0"} -> {:mvn/version "1.0.1"}
- mvxcvi/arrangement {:mvn/version "1.1.1"} -> {:mvn/version "1.2.0"}

# 0.0-25 (2018-11-10 / 2fab8b1)

## Fixed

- Fix support for records with inserts [#4](https://github.com/lambdaisland/deep-diff/pull/4)

## Changed

- Version upgrades:
- mvxcvi/puget {:mvn/version "1.0.2"} -> {:mvn/version "1.0.3"}
- fipp {:mvn/version "0.6.13"} -> {:mvn/version "0.6.14"}
- lambdaisland/kaocha {:mvn/version "0.0-239"} -> {:mvn/version "0.0-266"}
- lambdaisland/kaocha-junit-xml {:sha "fb06678e9f947cd7ff0deff456e8e6afae687afe"} -> {:sha "a35398d4bf553bdb09b8ef07f4bf8bd3bd40bc61"}

# 0.0-15 (2018-10-31 / 63d30b5)

## Fixed

- Fix support for Clojure records. Currently they are considered in the same
  equality partition as maps. (by [@ikitommi](https://github.com/ikitommi), [#1](https://github.com/lambdaisland/deep-diff/pull/1))
- Pin explicitly to the latest version of Fipp and rrb-vector, to prevent issues
  on Java 11, see [CRRBV-18](https://dev.clojure.org/jira/browse/CRRBV-18)

# 0.0-8 (2018-10-30 / 6bd7918)

## Changed

- `lambdaisland.deep-diff.printer/print-*` are now public, as they can be used
  in custom print handlers.

# 0.0-4 (2018-10-30 / 3d82596)

## Added

- Extracted from Kaocha, and added a top-level namespace.
