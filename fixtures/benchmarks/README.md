# Anvil Benchmarks

Standard scripted agent tasks with automatic scoring. Each benchmark runs a `ScriptedModel` against a fixture workspace and checks trace + filesystem assertions.

## Run

```bash
bash scripts/benchmark.sh
# or
mvn test -pl anvil-core -Dtest=BenchmarkSuiteTest
```

## Catalog

| ID | Goal |
|----|------|
| `read-add` | `fs.read` Add.java, ≤2 steps |
| `grep-hello` | `grep` without shell |
| `symbols-find` | `symbols.search` + `fs.read` |
| `patch-add` | `search_replace` bugfix on sample-lib-buggy |
| `ask-deny-write` | ASK mode denies write |
| `agent-write-approve` | Write + approval flow |

## Spec format

See `read-add.benchmark.json`. Register new ids in `manifest.json`.

Scoring: `com.anvil.core.benchmark.BenchmarkRunner` — one point per `expect` rule.
