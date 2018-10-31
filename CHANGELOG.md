# Unreleased

## Added

## Fixed

## Changed

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
