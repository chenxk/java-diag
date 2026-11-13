# java-diag

[中文](README.md)

Read-oriented, auditable MCP for diagnosing Java processes in production.

- **Probe** `java-diag-probe`: runs on the app host; HTTP MCP (also `--stdio`)
- **Gateway** `java-diag-gateway`: cluster entry + built-in heartbeat registry; Agents can talk only to the Gateway, or still connect to probes directly
- **Platforms**: Windows / macOS / Linux (process discovery: Linux prefers `/proc`; other platforms use `jps` / `ProcessHandle`)
- **License**: [MIT](LICENSE) (probe and Gateway are both open source)
- **Security**: [SECURITY.md](SECURITY.md) · **Contributing**: [CONTRIBUTING.md](CONTRIBUTING.md)

## Quick Start (GitHub Release — no local build)

Download [Releases](https://github.com/chenxk/java-diag/releases/latest) and run; no `mvn package`. Requires **JDK 17+**. Steps below are **Linux**-oriented; on Windows use `start.cmd` / `start.ps1` in the same directories.

The extracted root matches repo [`deploy/`](deploy/): `java-diag-gateway/`, `java-diag-probe/`, README, Dockerfiles, etc.

![deploy_dir.png](pics/deploy_dir.png)

Example values are strong placeholders — **use your own**; replace every `CHANGE_ME_*`. Full reference: [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

### A. Single-host direct probe (start here)

One machine: start only the probe; point the Agent at the probe `/mcp` (default port `8765`).

#### 1. Edit probe config

Edit `java-diag-probe/java-diag-probe.properties`:

| Key | Example | Notes |
|-----|---------|-------|
| `auth.token` | `jd_pr_T2wE5rY8uI0oP3aS` | Agent → probe MCP Bearer (**required**) |
| `register.token` | `jd_reg_H3cF6yL1bN5dZ0uE` | Required at startup; for standalone, any strong random value is fine until you join a Gateway |

No need to set `register.url` / `advertise.url` for standalone.

#### 2. Start the probe

```bash
cd java-diag-probe
chmod +x start.sh && ./start.sh start
```

#### 3. Configure MCP in your Agent (direct to probe)

Default port `8765`; Bearer = probe `auth.token`.

**Cursor**: project `.cursor/mcp.json`, or Cursor Settings → MCP.

```json
{
  "mcpServers": {
    "java-diag": {
      "url": "http://127.0.0.1:8765/mcp",
      "headers": { "Authorization": "Bearer jd_pr_T2wE5rY8uI0oP3aS" }
    }
  }
}
```

**Claude Desktop**: edit the config JSON (macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`; Windows: `%APPDATA%\Claude\claude_desktop_config.json`; Linux: `~/.config/Claude/claude_desktop_config.json`), add the same `mcpServers` block, then restart Claude.

Use the probe’s reachable host instead of `127.0.0.1` when remote.

#### 4. Verify

```bash
curl -s http://127.0.0.1:8765/health
```

Or call `diag.ping` from the Agent.

### B. Gateway + Probe cluster mode

Multi-host / multi-instance: start Gateway first, then register probes; the Agent talks **only to the Gateway** `/mcp` (default port `8787`).

#### 1. Minimal config (Gateway + Probe)

| File | Key | Example | Notes |
|------|-----|---------|-------|
| `java-diag-gateway/java-diag-gateway.properties` | `auth.token` | `jd_ag_K9mP2xQ7vR4nW8sT` | Agent → Gateway MCP Bearer |
| same | `register.token` | `jd_reg_H3cF6yL1bN5dZ0uE` | `/registry/*`; **must match probe** |
| same | `admin.password` | `jd_adm_Xp4Qw8Rm2Vn7Ks1Y` | Browser Admin password (change it) |
| `java-diag-probe/java-diag-probe.properties` | `auth.token` | `jd_pr_T2wE5rY8uI0oP3aS` | Bearer for Gateway → probe MCP (may differ from gateway `auth.token`) |
| same | `register.token` | `jd_reg_H3cF6yL1bN5dZ0uE` | **same as gateway `register.token`** |
| same | `register.url` | `http://10.0.1.10:8787` | Gateway root URL (no `/mcp`); localhost: `http://127.0.0.1:8787` |
| same | `advertise.url` | `http://10.0.1.11:8765` | Reachable probe URL for Gateway callbacks (set explicitly in containers / multi-NIC) |

#### 2. Start (Gateway first, then Probe)

```bash
cd java-diag-gateway
chmod +x start.sh && ./start.sh start

cd ../java-diag-probe
chmod +x start.sh && ./start.sh start
```

Optional Admin: open `http://<gateway-host>:8787/admin/`, sign in with `admin.user` / `admin.password`. Sidebar **探针实例** lists registered probes; **MCP 配置** or a row’s **查看 MCP** can **复制 URL** / **复制完整 JSON**.

![mcp_config_en.png](pics/mcp_config_en.png)
![probe_list_en.png](pics/probe_list_en.png)

#### 3. Configure MCP in your Agent (Gateway only)

Default port `8787`; Bearer = gateway `auth.token`.

```json
{
  "mcpServers": {
    "java-diag": {
      "url": "http://127.0.0.1:8787/mcp",
      "headers": { "Authorization": "Bearer jd_ag_K9mP2xQ7vR4nW8sT" }
    }
  }
}
```

Use the Gateway’s reachable host instead of `127.0.0.1` when remote. Cursor / Claude Desktop paths are the same as in section A.

#### 4. Verify

```bash
curl -s http://127.0.0.1:8787/health
```

Or call `diag.ping` from the Agent.

Source build, architecture, and full docs: sections below and [`docs/`](docs/).

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
  Agent["Agent / IDE"] -->|"HTTP MCP /mcp + Bearer"| Probe["java-diag-probe"]
  Probe --> Logs["Local log files"]
  Probe --> Jvm["Local JVM / jcmd"]
```

Good for one machine (see Quick Start **A**): point the Agent at the probe `http://host:8765/mcp`.

### Cluster (Agent → Gateway only)

```mermaid
flowchart TB
  Agent["Agent / IDE"] -->|"HTTP MCP /mcp + Bearer"| GW["java-diag-gateway"]

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
      "url": "http://10.0.1.11:8765/mcp",
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
      "url": "http://gateway-host:8787/mcp",
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
