# Java Diag — Kubernetes Deploy Notes (draft)

[中文](README.md)

Minimal runnable manifests for **Gateway + app Sidecar probe**.  
**Java Diag** does **not** need K8s-specific diagnostic logic; the key is placing the probe where it can see the app JVM and read logs.

> Image names, app start commands, and log paths are placeholders — replace them before production.

## Contents

| File | Description |
|------|-------------|
| `00-namespace-secrets.yaml` | Namespace `java-diag` + Token / Admin credentials |
| `10-gateway.yaml` | Cluster entry Gateway (Deployment + Service) |
| `20-app-with-sidecar.yaml` | **Recommended**: app Pod + probe Sidecar |
| `30-daemonset-alt.yaml` | Alternative: node-level DaemonSet (broader privileges; usually not preferred) |

## Architecture (brief)

```
Agent / IDE
    │  HTTP MCP /mcp + Bearer (/sse still supported)
    ▼
java-diag-gateway  (:8787)
    │  Pick probe from heartbeat registry (multi-replica returns candidates)
    ▼
App Pod
  ├─ app (business JVM)
  └─ java-diag sidecar (:8765)  ← shareProcessNamespace + shared log volume
```

## Prerequisites

1. Images built and pushed:
   - `your-registry/java-diag-gateway:<tag>`
   - `your-registry/java-diag-probe:<tag>` (prefer a JDK image that includes `jps` / `jcmd`)
2. Gateway in-cluster can reach each Pod’s **advertise** URL (default `http://<PodIP>:8765`)
3. App logs written to a directory the Sidecar can also mount (example: `/var/log/app`)

## Quick deploy

```bash
# 1) Replace every CHANGE_ME_* in 00-namespace-secrets.yaml with strong random values
# 2) Replace your-registry/... in the YAMLs with real images
# 3) Adjust app image and log paths in 20 as needed

kubectl apply -f 00-namespace-secrets.yaml
kubectl apply -f 10-gateway.yaml
kubectl apply -f 20-app-with-sidecar.yaml
```

Check:

```bash
kubectl -n java-diag get pods,svc
kubectl -n java-diag logs deploy/java-diag-gateway --tail=50
kubectl -n java-diag logs deploy/demo-order-service -c java-diag --tail=50
```

Admin console (expose Gateway to localhost or via Ingress):

- URL: `http://<gateway-host>:8787/admin/`
- Credentials in Secret: `admin-user` / `admin-password` (do not use repo placeholders)

## Sidecar must-haves (`20-app-with-sidecar.yaml`)

| Item | Purpose |
|------|---------|
| `shareProcessNamespace: true` | Sidecar shares PID namespace with the app so `jps` / `jcmd` work |
| Shared log Volume + `JAVA_DIAG_ALLOWED_LOG_DIRS` | Enables `diag.log_paths` / `grep_log` / `cluster_errors` |
| `JAVA_DIAG_REGISTER_URL` | Gateway **root** URL (do **not** append `/mcp`), e.g. `http://java-diag-gateway.java-diag.svc.cluster.local:8787` |
| `JAVA_DIAG_REGISTRY_REGISTER_TOKEN` | Must match Gateway `register.token` (Secret `register-token`) |
| `JAVA_DIAG_ADVERTISE_URL` | URL Gateway uses to call back; example `http://$(POD_IP):8765` |
| `JAVA_DIAG_SERVICE_NAME` / `JAVA_DIAG_TAGS` / `JAVA_DIAG_APP_NAME_MAP` | Discover instances by app name / tags |
| `JAVA_DIAG_AUTH_TOKEN` | Probe MCP Bearer (Secret `probe-token`; reported at register time; Gateway uses it to call the probe; may differ from Agent→Gateway `agent-token`) |

If the entrypoint does not expand `$(POD_IP)`, set `advertise.url` yourself in the start command.

## Connecting an Agent

Connect only to the Gateway (do not point at every sidecar unless troubleshooting). Prefer `/mcp` (`/sse` still supported):

```json
{
  "mcpServers": {
    "java-diag-cluster": {
      "url": "http://<gateway-host>:8787/mcp",
      "headers": { "Authorization": "Bearer YOUR_AGENT_TOKEN" }
    }
  }
}
```

Typical diagnosis flow:

1. `diag.list_instances` (filter by `app` / `serviceName`) or `diag.resolve_service`
2. On multi-replica, inspect `candidates`, then pick one and pass `target`
3. Call `diag.thread_dump`, `diag.grep_log`, `diag.cluster_errors`, etc. with `target` + `pid`

**Multi-replica never silently picks a machine.**

## DaemonSet alternative (`30-daemonset-alt.yaml`)

Use when you temporarily do not want to change every app Deployment.

Trade-offs:

- Needs `hostPID` (larger privilege surface)
- Container PID ↔ host PID mapping is more awkward
- Logs depend on `hostPath` / convention directories — less clear than Sidecar

Most scenarios still prefer Sidecar.

## FAQ

**Instances show up, but thread_dump / jcmd fails**  
Check `shareProcessNamespace`, and that the probe image has JDK tools compatible with the app JDK.

**Processes list OK, but logs not found**  
Confirm the app log dir is mounted into the sidecar and listed in `JAVA_DIAG_ALLOWED_LOG_DIRS`.

**Gateway shows no probe / heartbeat expired**  
Verify both sides use the same `JAVA_DIAG_REGISTRY_REGISTER_TOKEN` (`register.token`), that `JAVA_DIAG_REGISTER_URL` / `JAVA_DIAG_ADVERTISE_URL` are reachable in-cluster, NetworkPolicies, and Gateway heartbeat TTL.

**`name` / `mainClass` shows a dependency jar**  
For `-classpath ... MainClass` launches, use a probe build that includes the classpath parsing fix; or map a business name via `JAVA_DIAG_APP_NAME_MAP`.

## Related docs

- Full config reference: [`docs/CONFIGURATION.md`](../../docs/CONFIGURATION.md)
- Cluster capabilities & tools: repo root [`README_EN.md`](../../README_EN.md)
- Architecture: [`docs/TECH_ARCHITECTURE.md`](../../docs/TECH_ARCHITECTURE.md)
