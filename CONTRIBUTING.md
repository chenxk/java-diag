# Contributing

Thanks for helping improve java-diag.

## Prerequisites

- JDK **17+**
- Maven **3.8+**

## Build & test

```bash
mvn -q clean test package
```

Artifacts:

- `java-diag-mcp-server/target/java-diag-mcp-server.jar`
- `java-diag-gateway/target/java-diag-gateway.jar`

Copy jars into `deploy/*/ ` locally if you use the helper scripts — **do not commit jars**.

## Project layout

| Module | Role |
|--------|------|
| `java-diag-mcp-server` | Probe (MCP tools + optional heartbeat) |
| `java-diag-gateway` | Cluster entry + registry + tool proxy |
| `docs/` | Configuration & architecture |
| `deploy/` | Local scripts + Kubernetes drafts |

## Pull requests

1. Keep changes focused; avoid drive-by reformatting.
2. Add or update unit tests when behavior changes.
3. Update `docs/CONFIGURATION.md` / README when adding flags or tools.
4. Do not commit secrets, heap dumps, or fat jars.

## Security

See [SECURITY.md](SECURITY.md). Please report vulnerabilities privately.
