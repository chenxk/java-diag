# 配置参考 / Configuration Reference

本文档列出 **探针**（`java-diag-mcp-server`）与 **网关**（`java-diag-gateway`）的全部可配置项，来源为代码中的 `DiagConfig` / `GatewayConfig` 与入口 `main` 参数。

**加载顺序：** 环境变量 → CLI 覆盖（同名项以 CLI 为准）。  
**无独立配置文件：** 当前不支持 JSON/YAML/properties 配置文件，仅 env + CLI。

查看内置帮助：

```bash
java -jar java-diag-mcp-server.jar --help
java -jar java-diag-gateway.jar --help
```

---

## 1. 探针 Probe（`java-diag-mcp-server`）

### 1.1 传输与 HTTP

| 名称 | 类型 | 默认值 | 含义 | 示例 |
|------|------|--------|------|------|
| `JAVA_DIAG_TRANSPORT` | string | `http` | 传输方式。`http` / `sse` / `remote` → HTTP；`stdio` / `std` / `local` → stdio | `JAVA_DIAG_TRANSPORT=stdio` |
| `--http` | flag | （默认即 HTTP） | 强制 HTTP 传输 | `--http` |
| `--stdio` | flag | — | 强制 stdio（本地 IDE） | `--stdio` |
| `JAVA_DIAG_HTTP_HOST` / `--host` | string | `0.0.0.0` | HTTP 监听地址（仅 HTTP） | `--host 127.0.0.1` |
| `JAVA_DIAG_HTTP_PORT` / `--port` | int `1..65535` | `8765` | HTTP 监听端口 | `--port 8765` |
| `JAVA_DIAG_AUTH_TOKEN` / `--token` / `--auth-token` | string | 空（不鉴权） | Agent/调用方 Bearer token；未设置时启动会打 WARNING | `--token probe-secret` |

> HTTP 端点：`/sse`、`/mcp`、`/health`。生产环境务必设置 token。

### 1.2 安全与资源限制

| 名称 | 类型 | 默认值 | 含义 | 示例 |
|------|------|--------|------|------|
| `JAVA_DIAG_ALLOWED_LOG_DIRS` | string（逗号分隔路径） | `.`、`$HOME`、`/tmp`、`/var/log`（均为绝对路径规范化后） | 日志读取白名单目录；未设置则用默认四项 | `JAVA_DIAG_ALLOWED_LOG_DIRS=/var/log/app,/data/logs` |
| `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX` | string（正则） | 空（不过滤） | 限制可诊断进程的 main class | `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX=^com\\.example\\.` |
| `JAVA_DIAG_TOOL_TIMEOUT_MS` | long（ms） | `15000` | 普通工具子进程超时 | `JAVA_DIAG_TOOL_TIMEOUT_MS=20000` |
| （固定）dump 超时 | long（ms） | `30000` | heap/thread dump、`gc_histo` 等使用的超时；**无 env/CLI**，代码内常量 | — |
| `JAVA_DIAG_MAX_OUTPUT_BYTES` | int（bytes） | `262144`（256 KiB） | 子进程 stdout/stderr 最大捕获字节；超出截断 | `JAVA_DIAG_MAX_OUTPUT_BYTES=524288` |
| `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` | bool | `false` | 是否允许危险操作（当前：`diag.gc_histo` 的 `live=true` 会触发 Full GC） | `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED=true` |
| `JAVA_DIAG_AUDIT_PATH` | path | `./logs/java-diag-audit.log`（绝对路径规范化） | 审计日志文件路径 | `JAVA_DIAG_AUDIT_PATH=/var/log/java-diag-audit.log` |

布尔值接受：`1` / `true` / `yes` / `on` → true；`0` / `false` / `no` / `off` → false；其它回退默认。

### 1.3 实例标识与集群注册

| 名称 | 类型 | 默认值 | 含义 | 示例 |
|------|------|--------|------|------|
| `JAVA_DIAG_INSTANCE_ID` / `--instance-id` | string | `{hostname}-{pid}` | 探针实例 ID（注册与工具 envelope） | `--instance-id order-1` |
| `JAVA_DIAG_SERVICE_NAME` / `--service-name` | string | 空 | 服务名（注册与 `diag.list_instances` 过滤） | `--service-name order-service` |
| `JAVA_DIAG_TAGS` | string | 空 | 标签，逗号分隔 `key=value` | `JAVA_DIAG_TAGS=env=prod,az=a` |
| `--tag` | `key=value`（可重复） | — | 追加/覆盖单个标签（CLI 优先合并） | `--tag env=prod --tag az=a` |
| `JAVA_DIAG_REGISTER_URL` / `--register-url` | URL | 空 | Gateway 根地址；设置后启用心跳注册。**Registry 无共享密钥**（信任内网） | `--register-url http://gateway:8787` |
| `JAVA_DIAG_ADVERTISE_URL` / `--advertise-url` | URL | 空则推算 `http://{host}:{port}` | 注册到 Gateway 的对外可达 base URL | `--advertise-url http://10.0.1.11:8765` |
| `JAVA_DIAG_HEARTBEAT_INTERVAL_SECONDS` / `--heartbeat-interval-seconds` | long（秒） | `20` | 心跳间隔；运行时会 `max(5, value)` | `--heartbeat-interval-seconds 15` |

### 1.4 其它 CLI

| 名称 | 含义 |
|------|------|
| `--help` / `-h` | 打印帮助并退出 |
| `--list-tools` | 列出已注册工具名并退出 |

### 1.5 探针示例

```bash
# 单机 HTTP
export JAVA_DIAG_ALLOWED_LOG_DIRS=/var/log/app
export JAVA_DIAG_AUDIT_PATH=/var/log/java-diag-audit.log
java -jar java-diag-mcp-server.jar --host 0.0.0.0 --port 8765 --token probe-secret

# 注册到 Gateway
java -jar java-diag-mcp-server.jar \
  --port 8765 --token probe-secret \
  --service-name order-service --instance-id order-1 --tag env=prod \
  --register-url http://gateway-host:8787 \
  --advertise-url http://10.0.1.11:8765

# 本地 IDE stdio
java -jar java-diag-mcp-server.jar --stdio
```

---

## 2. 网关 Gateway（`java-diag-gateway`）

Gateway **仅 HTTP**（无 stdio / 无 `JAVA_DIAG_TRANSPORT`）。  
Agent→Gateway 用 Bearer token；`/registry/*` 与 `/health` **不做**共享密钥校验（信任内网，勿对公网暴露 Registry 端口）。

### 2.1 HTTP 与鉴权

| 名称 | 类型 | 默认值 | 含义 | 示例 |
|------|------|--------|------|------|
| `JAVA_DIAG_HTTP_HOST` / `--host` | string | `0.0.0.0` | HTTP 监听地址 | `--host 0.0.0.0` |
| `JAVA_DIAG_HTTP_PORT` / `--port` | int `1..65535` | `8787` | HTTP 监听端口 | `--port 8787` |
| `JAVA_DIAG_AUTH_TOKEN` / `--token` / `--auth-token` | string | 空（不鉴权） | Agent Bearer token（保护 `/sse`、`/mcp`）；未设置时启动 WARNING | `--token agent-secret` |

### 2.2 注册表 TTL 与实例标识

| 名称 | 类型 | 默认值 | 含义 | 示例 |
|------|------|--------|------|------|
| `JAVA_DIAG_HEARTBEAT_TTL_SECONDS` / `--heartbeat-ttl-seconds` | long（秒） | `90` | 探针心跳 TTL；必须 `>= 5`；超时未心跳则实例过期/不可路由 | `--heartbeat-ttl-seconds 90` |
| `JAVA_DIAG_INSTANCE_ID` | string | `gateway-{pid}` | Gateway 自身 instanceId（工具 envelope）；**仅环境变量，无 CLI** | `JAVA_DIAG_INSTANCE_ID=gw-1` |

> 探针心跳间隔应明显小于 Gateway TTL（例如探针 `20s`、Gateway TTL `90s`）。

### 2.3 固定超时（无 env/CLI）

代理调用探针时使用代码内常量（见 `GatewayToolRegistrar` / `ProbeMcpClient`）：

| 项 | 默认值 | 含义 |
|----|--------|------|
| probe request timeout | `60s` | 单次工具调用请求超时 |
| probe connect timeout | `10s` | 连接探针 SSE/HTTP 超时 |
| probe initialization timeout | `15s` | MCP initialize 超时 |

### 2.4 其它 CLI

| 名称 | 含义 |
|------|------|
| `--help` / `-h` | 打印帮助并退出 |

### 2.5 端点

| 路径 | 用途 | 鉴权 |
|------|------|------|
| `/sse`、`/mcp` | Agent MCP（含 `diag.list_instances` 与代理工具） | 若配置了 token，需 `Authorization: Bearer …` |
| `/registry/*` | 探针 register / heartbeat / deregister / instances | 无共享密钥（信任内网） |
| `/health` | 存活探测 | 无 |

### 2.6 Gateway 示例

```bash
# 推荐：设置 Agent token + TTL
export JAVA_DIAG_HEARTBEAT_TTL_SECONDS=90
export JAVA_DIAG_INSTANCE_ID=gw-1
java -jar java-diag-gateway.jar \
  --host 0.0.0.0 --port 8787 --token agent-secret

# 等价 CLI
java -jar java-diag-gateway.jar \
  --host 0.0.0.0 --port 8787 --token agent-secret \
  --heartbeat-ttl-seconds 90
```

---

## 3. 环境变量一览（速查）

### Probe

`JAVA_DIAG_TRANSPORT` · `JAVA_DIAG_HTTP_HOST` · `JAVA_DIAG_HTTP_PORT` · `JAVA_DIAG_AUTH_TOKEN` · `JAVA_DIAG_ALLOWED_LOG_DIRS` · `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX` · `JAVA_DIAG_TOOL_TIMEOUT_MS` · `JAVA_DIAG_MAX_OUTPUT_BYTES` · `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` · `JAVA_DIAG_AUDIT_PATH` · `JAVA_DIAG_INSTANCE_ID` · `JAVA_DIAG_SERVICE_NAME` · `JAVA_DIAG_TAGS` · `JAVA_DIAG_REGISTER_URL` · `JAVA_DIAG_ADVERTISE_URL` · `JAVA_DIAG_HEARTBEAT_INTERVAL_SECONDS`

### Gateway

`JAVA_DIAG_HTTP_HOST` · `JAVA_DIAG_HTTP_PORT` · `JAVA_DIAG_AUTH_TOKEN` · `JAVA_DIAG_HEARTBEAT_TTL_SECONDS` · `JAVA_DIAG_INSTANCE_ID`

> Probe 与 Gateway 共用部分 env 名（`JAVA_DIAG_HTTP_*`、`JAVA_DIAG_AUTH_TOKEN`、`JAVA_DIAG_INSTANCE_ID`）。同机同进程环境勿混用未区分的值；分进程启动时各自设置即可。

---

## 4. 源码锚点

| 组件 | 类 |
|------|-----|
| Probe 配置 | `java-diag-mcp-server/.../config/DiagConfig.java` |
| Probe 入口 / `--help` | `java-diag-mcp-server/.../JavaDiagMcpServer.java` |
| Gateway 配置 | `java-diag-gateway/.../config/GatewayConfig.java` |
| Gateway 入口 / `--help` | `java-diag-gateway/.../JavaDiagGateway.java` |
