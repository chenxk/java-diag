# Deploy helpers

- `java-diag-mcp-server/` / `java-diag-gateway/` — start scripts + properties templates  
- `Dockerfile.probe` / `Dockerfile.gateway` — container images  
- `k8s/` — Kubernetes sidecar / DaemonSet drafts

**Fat jars are not committed.** After build:

```bash
mvn -q clean package
cp java-diag-mcp-server/target/java-diag-mcp-server.jar deploy/java-diag-mcp-server/
cp java-diag-gateway/target/java-diag-gateway.jar deploy/java-diag-gateway/
```

Replace every `CHANGE_ME_*` in properties / Secrets before shared use. See [SECURITY.md](../SECURITY.md).
