# Java Diag — Deploy helpers

[中文](README.md)

Scripts and templates for **Java Diag** release / local deploy; layout matches the [GitHub Release](https://github.com/chenxk/java-diag/releases/latest) archive.

- `java-diag-probe/` / `java-diag-gateway/` — `start.sh` (and Windows `start.cmd` / `start.ps1`) + properties templates  
- `Dockerfile.probe` / `Dockerfile.gateway` — container images  
- `k8s/` — Kubernetes sidecar / DaemonSet drafts (see [`k8s/README_EN.md`](k8s/README_EN.md))

**Full getting-started (single-host probe → Gateway+Probe, Agent `/mcp`) is in the repo root [README_EN.md](../README_EN.md) Quick Start**; this page only covers deploy highlights.

## Build artifacts

**Fat jars are not checked in.** After build, copy:

```bash
mvn -q clean package
cp java-diag-probe/target/java-diag-probe.jar deploy/java-diag-probe/
cp java-diag-gateway/target/java-diag-gateway.jar deploy/java-diag-gateway/
```

## Start and ports

```bash
# Probe (default MCP :8765; standalone can start only this)
cd deploy/java-diag-probe
chmod +x start.sh && ./start.sh start   # stop | restart | status | foreground

# Gateway (default :8787; in a cluster start Gateway first, then Probe)
cd deploy/java-diag-gateway
chmod +x start.sh && ./start.sh start
```

- Agent preferred HTTP MCP: `http://<host>:8765/mcp` (direct probe) or `http://<host>:8787/mcp` (Gateway only); `/sse` still supported
- Admin: `http://<gateway-host>:8787/admin/` (`admin.user` / `admin.password`)

## Config highlights

Replace every `CHANGE_ME_*` in properties (or use K8s Secrets). Key fields:

| Role | Key | Notes |
|------|-----|-------|
| Probe / Gateway | `auth.token` | MCP Bearer for each side (may differ); CLI override `--auth-token` (no `--token` alias) |
| Probe / Gateway | `register.token` | **Must match** in a cluster; required on the probe only when `register.url` is set; always required on Gateway |
| Gateway | `admin.user` / `admin.password` | Browser Admin |
| Probe (when using Gateway) | `register.url` / `advertise.url` | Gateway root URL (no `/mcp`); reachable probe callback address |

Probe-only on a single host: set `auth.token`; leave `register.url` / `register.token` empty. When joining a Gateway, both sides' `register.token` must match. Full list: [docs/CONFIGURATION.md](../docs/CONFIGURATION.md); shared environments: [SECURITY.md](../SECURITY.md).
