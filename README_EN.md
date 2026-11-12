# java-diag

[中文](README.md)

Read-oriented, auditable MCP for diagnosing Java processes in production.

- **Probe** `java-diag-probe`: runs on the app host; HTTP MCP (also `--stdio`)
- **Gateway** `java-diag-gateway`: cluster entry + built-in heartbeat registry; Agents can talk only to the Gateway, or still connect to probes directly
- **Platforms**: Windows / macOS / Linux (process discovery: Linux prefers `/proc`; other platforms use `jps` / `ProcessHandle`)
- **License**: [MIT](LICENSE) (probe and Gateway are both open source)
- **Security**: [SECURITY.md](SECURITY.md) · **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)

## How it works

**In one line**: diagnostics run on the probe host; the Gateway only does discovery/registry and MCP tool proxying — **it does not run jcmd or read application logs itself**.

### Roles

| Role | Does | Does not |
|------|------|----------|
| **Agent** (Cursor, etc.) | Call MCP tools with `target` / `pid` | Talk directly to the business JVM |
| **Gateway** | Auth, heartbeat Registry, pick a node via `target`, forward tool calls to probes | Collect logs / JVM data |
| **Probe** | Local process discovery, whitelisted log reads, `jps`/`jcmd`, etc. | Reach into other nodes |

### Single host (Agent → probe)

```mermaid
flowchart LR
  Agent["Agent / IDE"] -->|"MCP SSE + Bearer"| Probe["java-diag-probe"]
  Probe --> Logs["Local log files"]
  Probe --> Jvm["Local JVM / jcmd"]
```

Good for one machine: point the Agent at the probe `http://host:8765/sse`.

### Cluster (Agent → Gateway only)

```mermaid
flowchart TB
  Agent["Agent / IDE"] -->|"MCP SSE + Bearer"| GW["java-diag-gateway"]

  subgraph registry ["Built-in Registry"]
    HB["Heartbeat registry<br/>instanceId / mcpBaseUrl / apps[] / tags"]
  end

  GW <-->|"register / heartbeat"| HB
  GW -->|"Resolve target → unique probe"| ProbeA["Probe A<br/>app host / Pod"]
  GW -->|"Short MCP callTool"| ProbeB["Probe B"]
  GW -->|"Multiple hits → candidates"| ProbeN["Probe …"]

  ProbeA --> LocalA["Local logs + JVM"]
  ProbeB --> LocalB["Local logs + JVM"]
```

**How a request lands on a specific node:**

1. On start, the probe registers with the Gateway using `--register-url` / `--advertise-url` and heartbeats periodically. The Registry stores `mcpBaseUrl` (reachable probe URL) and local `apps[]` (JVM inventory).
2. The Agent calls tools with `target` (`instanceId` / `mcpBaseUrl` / `serviceName`+`tags`), or first uses `diag.resolve_service` / `diag.list_instances` to pick a host.
3. The Gateway resolves against the Registry:
   - **0 hits** → `INSTANCE_NOT_FOUND`
   - **Multiple** → returns `candidates`, **never silently picks**
   - **Exactly 1** → acts as an MCP client, short-connects to that probe’s `mcpBaseUrl`, forwards args (minus `target`) and returns the result as-is (including `routedInstanceId`)

```text
Agent ──MCP──▶ Gateway ──pick node──▶ Probe MCP ──local exec──▶ logs / jcmd
                 ▲                       │
                 └──── heartbeat reg ────┘
```

Therefore: **the Gateway must be able to reach each probe’s advertise address**; on Kubernetes, typically one Sidecar probe per Pod (see [`deploy/k8s/README.md`](deploy/k8s/README.md)).

More module/tool detail: [`docs/TECH_ARCHITECTURE.md`](docs/TECH_ARCHITECTURE.md).

## Build

```bash
mvn -q clean package
```

Artifacts (fat jars, **not committed**; release via CI artifact / GitHub Release):

- `java-diag-probe/target/java-diag-probe.jar`
- `java-diag-gateway/target/java-diag-gateway.jar`

Local script dirs under `deploy/java-diag-*/` need the jars copied in:

```bash
cp java-diag-probe/target/java-diag-probe.jar deploy/java-diag-probe/
cp java-diag-gateway/target/java-diag-gateway.jar deploy/java-diag-gateway/
```

Docker (run `mvn package` first):

```bash
docker build -f deploy/Dockerfile.probe -t java-diag-probe:local .
docker build -f deploy/Dockerfile.gateway -t java-diag-gateway:local .
```

## Configuration

Full CLI / env coverage (transport, host/port, tokens, log whitelist, timeouts, output caps, dangerous tools, audit path, registration & TTL, etc.):

**[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**

```bash
java -jar java-diag-probe.jar --help
java -jar java-diag-gateway.jar --help
```

Copy example properties from each module’s `src/main/resources/*.properties.example` into the working directory or pass `--config`.

Load order: **built-in defaults → properties file → environment variables → CLI**.

## Single-host probe

```bash
cp java-diag-probe/src/main/resources/java-diag-probe.properties.example \
   ./java-diag-probe.properties
# Edit auth.token / register.token (required; do not leave placeholders), allowed.log.dirs, etc.
java -jar java-diag-probe/target/java-diag-probe.jar \
  --config ./java-diag-probe.properties
```

Agent direct to probe:

```json
{
  "mcpServers": {
    "java-diag": {
      "url": "http://10.0.1.11:8765/sse",
      "headers": { "Authorization": "Bearer YOUR_PROBE_TOKEN" }
    }
  }
}
```

## Cluster (heartbeat registry)

### 1) Start Gateway

```bash
java -jar java-diag-gateway/target/java-diag-gateway.jar \
  --config ./java-diag-gateway.properties
```

Both `auth.token` and `register.token` are **required** (blank → startup fails); probe and gateway `register.token` values must match.

**Admin console** (browse registered probes): `http://<gateway-host>:8787/admin/`  
Credentials come from `admin.user` / `admin.password` (examples use `CHANGE_ME_*` placeholders — **change them before deploy**).

### 2) Start probe and register

`--service-name` / `JAVA_DIAG_SERVICE_NAME` is optional (defaults to the primary NIC IP). There is **no** `--instance-id`: identity is derived from `--advertise-url`’s `host:port` (same as Registry `mcpBaseUrl`).

```bash
java -jar java-diag-probe/target/java-diag-probe.jar \
  --config ./java-diag-probe.properties \
  --register-url http://gateway-host:8787 \
  --advertise-url http://10.0.1.11:8765
```

### 3) Agent connects only to Gateway

```json
{
  "mcpServers": {
    "java-diag-cluster": {
      "url": "http://gateway-host:8787/sse",
      "headers": { "Authorization": "Bearer YOUR_AGENT_TOKEN" }
    }
  }
}
```

Playbook (by JVM / app name):

1. `diag.resolve_service` `{ "name": "order-service", "tags": { "env": "prod" } }` → unique hit yields `suggestedTarget`; multiple hits → inspect `candidates`
2. Call `diag.thread_dump`, `diag.cpu_sample`, etc. with `target` (`instanceId` / `mcpBaseUrl`) + `pid`

Alternatively: `diag.list_instances` (supports `app` / `serviceName` filters) → review `candidates` on multi-hit → then set `target`.  
**Replicas are never silently selected.**

On the probe, use `JAVA_DIAG_APP_NAME_MAP` / `--app-map` to map jar/mainClass to `apps[].name` for business-name resolution.

## Locked decisions (cluster)

| Item | Decision |
|------|----------|
| Discovery | Heartbeat registration (built-in Registry) |
| Gateway | Separate jar (MIT, same as probe) |
| Direct probe | Allowed |
| Multi-replica | Return candidates; Agent chooses |
| Auth | `auth.token` (Agent/MCP) and `register.token` (`/registry/*`) are both required and non-empty |

Architecture: [docs/TECH_ARCHITECTURE.md](docs/TECH_ARCHITECTURE.md) · Full config: [docs/CONFIGURATION.md](docs/CONFIGURATION.md) · K8s draft: [deploy/k8s/README.md](deploy/k8s/README.md)

## Tools (probe)

Meta / Logs / JVM: `diag.ping`, `diag.list_processes`, `diag.resolve_java_pid`, `diag.log_paths`, `diag.tail_log`, `diag.grep_log`, `diag.cluster_errors`, `diag.runtime_summary`, `diag.jstat`, `diag.thread_dump`, `diag.vm_flags`, `diag.gc_histo`, `diag.vm_info`, `diag.heap_dump`, `diag.cpu_sample`.  
Gateway additionally provides `diag.list_instances`, `diag.resolve_service`, and proxies the probe tools above.

## Tests

```bash
mvn -q test
```

CI: `.github/workflows/ci.yml` (JDK 17 / 21).

## License

[MIT](LICENSE)
