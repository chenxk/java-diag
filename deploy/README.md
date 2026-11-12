# Deploy helpers

- `java-diag-probe/` / `java-diag-gateway/` — start scripts + properties templates  
- `Dockerfile.probe` / `Dockerfile.gateway` — container images  
- `k8s/` — Kubernetes sidecar / DaemonSet drafts

**Fat jars are not committed.** After build:

```bash
mvn -q clean package
cp java-diag-probe/target/java-diag-probe.jar deploy/java-diag-probe/
cp java-diag-gateway/target/java-diag-gateway.jar deploy/java-diag-gateway/
```

Replace every `CHANGE_ME_*` in properties / Secrets before shared use. See [SECURITY.md](../SECURITY.md).
