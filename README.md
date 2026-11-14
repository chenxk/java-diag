# Java Diag

[English](README_EN.md)

**Java Diag** 是只读、可审计的 Java 线上排查 MCP。

- **探针** `java-diag-probe`：部署在业务机，HTTP MCP（也可 `--stdio`）
- **网关** `java-diag-gateway`：集群入口 + 内置心跳注册；Agent 可只连 Gateway，也仍可直连探针
- **平台**：Windows / macOS / Linux（进程发现：Linux 优先 `/proc`，其它平台走 `jps` / `ProcessHandle`）
- **License**：[MIT](LICENSE)（探针与 Gateway 均开源）
- **安全说明**：[SECURITY.md](SECURITY.md) · **贡献**：[CONTRIBUTING.md](CONTRIBUTING.md)

## Quick Start（GitHub Release）

点击下载 [Releases](https://github.com/chenxk/java-diag/releases/latest) 。需要本机 **JDK 17+**。以下以 **Linux** 为例；Windows 用同目录下的 `start.cmd` / `start.ps1`。

解压后根目录与仓库 [`deploy/`](deploy/) 一致，至少包含 `java-diag-gateway/`、`java-diag-probe/`、README、Dockerfile 等。

![deploy_dir.png](pics/deploy_dir.png)

配置示例均为自拟强随机值，**请换成你自己的**；勿再使用 `CHANGE_ME_*`。完整配置见 [docs/CONFIGURATION.md](docs/CONFIGURATION.md)。

### A. 单机直连探针（推荐首轮）

一台机器排查：只启探针，Agent 直连探针 `/mcp`（默认端口 `8765`）。

#### 1. 改探针配置

编辑 `java-diag-probe/java-diag-probe.properties`：

| 键 | 示例值 | 说明 |
|----|--------|------|
| `auth.token` | `jd_pr_T2wE5rY8uI0oP3aS` | Agent → 探针 MCP Bearer（**必改**） |

单机不必填 `register.url` / `advertise.url` / `register.token`（仅挂 Gateway 时 `register.token` 才必填）。

#### 2. 启动探针

```bash
cd java-diag-probe
chmod +x start.sh && ./start.sh start
```

#### 3. 在 Agent 里配 MCP（直连探针）

URL 默认端口 `8765`；Bearer 填探针的 `auth.token`。

**Cursor**：项目根 `.cursor/mcp.json`，或 Cursor Settings → MCP。

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

**Claude Desktop**：编辑配置 JSON（macOS：`~/Library/Application Support/Claude/claude_desktop_config.json`；Windows：`%APPDATA%\Claude\claude_desktop_config.json`；Linux：`~/.config/Claude/claude_desktop_config.json`），写入同样的 `mcpServers` 段后重启 Claude。

远程时把 `127.0.0.1` 换成探针可达地址。

#### 4. 验证

```bash
curl -s http://127.0.0.1:8765/health
```

或在 Agent 里调用 `diag.ping`。

### B. Gateway + Probe 集群模式

多机 / 多实例：先启 Gateway，再启探针注册；Agent **只连 Gateway** `/mcp`（默认端口 `8787`）。

#### 1. 改配置（Gateway + Probe）

| 文件 | 键 | 示例值 | 说明 |
|------|-----|--------|------|
| `java-diag-gateway/java-diag-gateway.properties` | `auth.token` | `jd_ag_K9mP2xQ7vR4nW8sT` | Agent → Gateway 的 MCP Bearer |
| 同上 | `register.token` | `jd_reg_H3cF6yL1bN5dZ0uE` | `/registry/*`；**须与探针相同** |
| 同上 | `admin.password` | `jd_adm_Xp4Qw8Rm2Vn7Ks1Y` | 浏览器 Admin 密码（建议改） |
| `java-diag-probe/java-diag-probe.properties` | `auth.token` | `jd_pr_T2wE5rY8uI0oP3aS` | Gateway 调探针 MCP 的 Bearer（可与网关 `auth.token` 不同） |
| 同上 | `register.token` | `jd_reg_H3cF6yL1bN5dZ0uE` | **与网关 `register.token` 一致** |
| 同上 | `register.url` | `http://10.0.1.10:8787` | Gateway 根 URL（不要带 `/mcp`）；本机可 `http://127.0.0.1:8787` |
| 同上 | `advertise.url` | `http://10.0.1.11:8765` | Gateway 回调探针的可达地址（容器 / 多网卡建议显式填写） |

#### 2. 启动（先 Gateway，后 Probe）

```bash
cd java-diag-gateway
chmod +x start.sh && ./start.sh start

cd ../java-diag-probe
chmod +x start.sh && ./start.sh start
```

可选 Admin：浏览器打开 `http://<gateway-host>:8787/admin/`，用 `admin.user` / `admin.password` 登录。侧栏「探针实例」可看已注册 Probe；「MCP 配置」或探针行的「查看 MCP」可「复制 URL」/「复制完整 JSON」。

![mcp_config.png](pics/mcp_config.png)
![probe_list.png](pics/probe_list.png)

#### 3. 在 Agent 里配 MCP（只连 Gateway）

URL 默认端口 `8787`；Bearer 填网关的 `auth.token`。

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

远程时把 `127.0.0.1` 换成 Gateway 可达地址。Cursor / Claude Desktop 配置路径同「单机」段。

#### 4. 验证

```bash
curl -s http://127.0.0.1:8787/health
```

或在 Agent 里调用 `diag.ping`。

源码构建、架构与全量说明见下文与 [`docs/`](docs/)。

## 实现原理

**一句话**：诊断能力在探针本机执行；Gateway 只做注册发现与 MCP 工具代理，**自己不跑 jcmd、不读业务日志**。

### 角色

| 角色 | 做什么 | 不做什么 |
|------|--------|----------|
| **Agent**（Cursor 等） | 调 MCP 工具、带 `target` / `pid` | 不直连业务 JVM |
| **Gateway** | 鉴权、心跳 Registry、按 `target` 选节点、把 tool call 转发到探针 | 不采集日志 / JVM |
| **Probe** | 本机进程发现、读白名单日志、`jps`/`jcmd` 等 | 不跨机抓别的节点 |

### 单机（Agent 直连探针）

```mermaid
flowchart LR
  Agent["Agent / IDE"] -->|"HTTP MCP /mcp + Bearer"| Probe["java-diag-probe"]
  Probe --> Logs["本机日志文件"]
  Probe --> Jvm["本机 JVM / jcmd"]
```

适合一台机器排查（见 Quick Start **A**）：Agent 配置探针的 `http://host:8765/mcp`。

### 集群（Agent 只连 Gateway）

```mermaid
flowchart TB
  Agent["Agent / IDE"] -->|"HTTP MCP /mcp + Bearer"| GW["java-diag-gateway"]

  subgraph registry ["Built-in Registry"]
    HB["心跳注册表<br/>instanceId / mcpBaseUrl / apps[] / tags"]
  end

  GW <-->|"register / heartbeat"| HB
  GW -->|"解析 target → 唯一探针"| ProbeA["Probe A<br/>业务机 / Pod"]
  GW -->|"MCP 短连接 callTool"| ProbeB["Probe B"]
  GW -->|"多命中则返回 candidates"| ProbeN["Probe …"]

  ProbeA --> LocalA["本机日志 + JVM"]
  ProbeB --> LocalB["本机日志 + JVM"]
```

**请求怎么落到具体节点：**

1. 探针启动时带 `--register-url` / `--advertise-url` 向 Gateway 注册，并定期心跳；Registry 里记下 `mcpBaseUrl`（对外可达的探针地址）和本机 `apps[]`（JVM 目录）。
2. Agent 调用工具时带上 `target`（`instanceId` / `mcpBaseUrl` / `serviceName`+`tags`），或先用 `diag.resolve_service` / `diag.list_instances` 选机。
3. Gateway 在 Registry 里解析：
   - **0 个** → `INSTANCE_NOT_FOUND`
   - **多个** → 返回 `candidates`，**不静默选机**
   - **正好 1 个** → 作为 MCP 客户端，短连接该探针的 `mcpBaseUrl`，转发去掉 `target` 后的参数并原样回传结果（含 `routedInstanceId`）

```text
Agent ──MCP──▶ Gateway ──选节点──▶ Probe MCP ──本机执行──▶ 日志 / jcmd
                 ▲                    │
                 └──── 心跳注册 ───────┘
```

因此：**网络上 Gateway 必须能访问各探针的 advertise 地址**；K8s 场景通常每 Pod 挂 Sidecar 探针（见 [`deploy/k8s/README.md`](deploy/k8s/README.md)）。

更细的模块与工具约定见 [`docs/TECH_ARCHITECTURE.md`](docs/TECH_ARCHITECTURE.md)。

## 构建

```bash
mvn -q clean package
```

产物（fat jar，**不入库**；发版请用 CI artifact / GitHub Release）：

- `java-diag-probe/target/java-diag-probe.jar`
- `java-diag-gateway/target/java-diag-gateway.jar`

本地脚本目录 `deploy/java-diag-*/` 需要自行拷贝 jar：

```bash
cp java-diag-probe/target/java-diag-probe.jar deploy/java-diag-probe/
cp java-diag-gateway/target/java-diag-gateway.jar deploy/java-diag-gateway/
```

Docker（先 `mvn package`）：

```bash
docker build -f deploy/Dockerfile.probe -t java-diag-probe:local .
docker build -f deploy/Dockerfile.gateway -t java-diag-gateway:local .
```

## 配置

全部 CLI / 环境变量（传输、host/port、token、日志白名单、超时、输出上限、危险工具、审计路径、注册与 TTL 等）见：

**[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**

```bash
java -jar java-diag-probe.jar --help
java -jar java-diag-gateway.jar --help
```

示例 properties（模块内 `src/main/resources/*.properties.example`）复制到工作目录或 `--config` 指定。

加载顺序：**内置默认 → properties 文件 → 环境变量 → CLI**。

## 单机探针

```bash
cp java-diag-probe/src/main/resources/java-diag-probe.properties.example \
   ./java-diag-probe.properties
# 编辑 auth.token（必填，勿用占位值）、allowed.log.dirs 等；单机可不设 register.token
java -jar java-diag-probe/target/java-diag-probe.jar \
  --config ./java-diag-probe.properties
```

Agent 直连：

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

## 集群（心跳注册）

### 1) 启动 Gateway

```bash
java -jar java-diag-gateway/target/java-diag-gateway.jar \
  --config ./java-diag-gateway.properties
```

Gateway 的 `auth.token` 与 `register.token` 均为必填（空则启动失败）；探针在设置 `register.url` 后须填 `register.token`，且与网关一致。

**管理控制台**（浏览器查看已注册探针）：`http://<gateway-host>:8787/admin/`  
账号由 `admin.user` / `admin.password` 配置（示例里为占位 `CHANGE_ME_*`，部署前必须改掉）。

### 2) 启动探针并注册

`--service-name` / `JAVA_DIAG_SERVICE_NAME` 可选（默认本机主网卡 IP）。**无** `--instance-id`：实例身份由 `--advertise-url` 的 `host:port` 推导（与 Registry `mcpBaseUrl` 一致）。

```bash
java -jar java-diag-probe/target/java-diag-probe.jar \
  --config ./java-diag-probe.properties \
  --register-url http://gateway-host:8787 \
  --advertise-url http://10.0.1.11:8765
```

### 3) Agent 只连 Gateway

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

剧本（按 JVM / 业务名）：

1. `diag.resolve_service` `{ "name": "order-service", "tags": { "env": "prod" } }` → 唯一命中得 `suggestedTarget`；多命中看 `candidates`
2. 带 `target`（`instanceId` / `mcpBaseUrl`）+ `pid` 调用 `diag.thread_dump`、`diag.cpu_sample` 等

也可：`diag.list_instances`（支持 `app` / `serviceName` 过滤）→ 多命中时看 `candidates` → 再指定 `target`。  
**多副本不会静默选机。**

探针侧可用 `JAVA_DIAG_APP_NAME_MAP` / `--app-map` 把 jar/mainClass 映射为 `apps[].name`，便于按业务名解析。

## 锁定决策（集群）

| 项 | 结论 |
|----|------|
| 发现 | 心跳注册（Built-in Registry） |
| Gateway | 独立 jar（与探针同为 MIT） |
| 直连探针 | 允许 |
| 多副本 | 返回 candidates，Agent 选 |
| 鉴权 | `auth.token`（Agent/MCP）必填；探针 `register.token` 仅在 `register.url` 非空时必填；Gateway `register.token` 始终必填；两端须一致 |

架构细节：[docs/TECH_ARCHITECTURE.md](docs/TECH_ARCHITECTURE.md) · 配置全集：[docs/CONFIGURATION.md](docs/CONFIGURATION.md) · K8s 草稿：[deploy/k8s/README.md](deploy/k8s/README.md)

## 工具（探针）

Meta / Logs / JVM：`diag.ping`、`diag.list_processes`、`diag.resolve_java_pid`、`diag.log_paths`、`diag.tail_log`、`diag.grep_log`、`diag.cluster_errors`、`diag.runtime_summary`、`diag.jstat`、`diag.thread_dump`、`diag.vm_flags`、`diag.gc_histo`、`diag.vm_info`、`diag.heap_dump`、`diag.cpu_sample`。  
Gateway 另提供 `diag.list_instances`、`diag.resolve_service`，并代理上述探针工具。

## 测试

```bash
mvn -q test
```

CI：`.github/workflows/ci.yml`（JDK 17 / 21）。

## License

[MIT](LICENSE)
