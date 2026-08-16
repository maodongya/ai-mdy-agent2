# Cross-module refactor

Find the interface, update all implementors, then run `mvn test -am`.

Expected tool flow: `symbols.search` → `codebase.search` → `search_replace` → verify.
