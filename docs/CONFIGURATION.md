# 配置参考 / Configuration Reference

探针（`java-diag-mcp-server`）与网关（`java-diag-gateway`）的配置项。

> **完整参数表**：以 classpath 内 `*.properties.example` 为准（每个可配置键均列出 `key=默认值`）。本文档说明加载顺序、CLI 映射与 env 附录；改配置时优先复制 example 再按需修改。

- 探针：`java-diag-mcp-server/src/main/resources/java-diag-mcp-server.properties.example`
- 网关：`java-diag-gateway/src/main/resources/java-diag-gateway.properties.example`

## 加载顺序（推荐）

1. **内置默认值**（代码常量）
2. **Properties 配置文件**（主推）
3. **环境变量**（可选覆盖，保持兼容）
4. **CLI**（薄入口，最高优先级）

| 场景 | 行为 |
|------|------|
| 未指定 `--config` | 若当前工作目录存在 `./java-diag-mcp-server.properties` 或 `./java-diag-gateway.properties` 则自动加载；不存在则跳过 |
| `--config /path/to/file` | 必须存在，否则启动失败并提示 |
| 显式路径缺失 | 报错（copy from `*.properties.example`） |

Properties 内可使用 **点分键**（推荐）或 legacy **`JAVA_DIAG_*` env 同名键**。

**安全**：`auth.token` 与 `register.token` 均为**必填且不可为空**（探针与网关都强制）；空值启动失败。探针与网关的 `register.token` 须一致。

```bash
java -jar java-diag-mcp-server.jar --help
java -jar java-diag-gateway.jar --help
```

---

## 1. 探针 Probe（`java-diag-mcp-server`）

### 1.0 Properties 键 ↔ 含义 ↔ 默认

| Properties 键 | 兼容 env | 默认值 | 含义 |
|---------------|----------|--------|------|
| `transport` | `JAVA_DIAG_TRANSPORT` | `http` | `http`/`stdio`（别名 sse/remote→http，stdio/std/local→stdio） |
| `http.host` | `JAVA_DIAG_HTTP_HOST` | `0.0.0.0` | HTTP 监听地址 |
| `http.port` | `JAVA_DIAG_HTTP_PORT` | `8765` | HTTP 监听端口 |
| `auth.token` | `JAVA_DIAG_AUTH_TOKEN` | **必填** | Agent / MCP Bearer；空则启动失败 |
| `allowed.log.dirs` | `JAVA_DIAG_ALLOWED_LOG_DIRS` | 平台默认目录 | 逗号分隔日志白名单 |
| `allowed.main.class.regex` | `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX` | 空 | main class 正则过滤 |
| `tool.timeout.ms` | `JAVA_DIAG_TOOL_TIMEOUT_MS` | `15000` | 工具子进程超时 |
| `max.output.bytes` | `JAVA_DIAG_MAX_OUTPUT_BYTES` | `262144` | stdout/stderr 捕获上限 |
| `dangerous.tools.enabled` | `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` | `false` | `live` GC、`heap_dump` 等 |
| `audit.path` | `JAVA_DIAG_AUDIT_PATH` | `./logs/java-diag-audit.log` | 审计日志 |
| `dump.cooldown.ms` | `JAVA_DIAG_DUMP_COOLDOWN_MS` | `10000` | 昂贵工具冷却 |
| `dump.timeout.ms` | `JAVA_DIAG_DUMP_TIMEOUT_MS` | `30000` | heap/thread dump 等子进程超时 |
| `service.name` | `JAVA_DIAG_SERVICE_NAME` | 主网卡 IP | 集群服务名 |
| `register.url` | `JAVA_DIAG_REGISTER_URL` | 空 | Gateway 根 URL，设置后启用心跳 |
| `register.token` | `JAVA_DIAG_REGISTRY_REGISTER_TOKEN` | **必填** | Gateway `/registry/*` Bearer；须与网关一致 |
| `advertise.url` | `JAVA_DIAG_ADVERTISE_URL` | 空 | 对外 MCP base URL；决定 `instanceId` |
| `heartbeat.interval.seconds` | `JAVA_DIAG_HEARTBEAT_INTERVAL_SECONDS` | `20` | 心跳间隔（有效 ≥5） |
| `tags` | `JAVA_DIAG_TAGS` | 空 | `k=v,k2=v2` |
| `app.name.map` | `JAVA_DIAG_APP_NAME_MAP` | 空 | `pattern=name` 映射 |

### 1.1 CLI（薄入口，覆盖文件）

| CLI | 对应 properties / env |
|-----|------------------------|
| `--config <file>` | 配置文件路径 |
| `--http` / `--stdio` | `transport` |
| `--host` | `http.host` |
| `--port` | `http.port` |
| `--token` / `--auth-token` | `auth.token` |
| `--service-name` | `service.name` |
| `--register-url` | `register.url` |
| `--register-token` | `register.token` |
| `--advertise-url` | `advertise.url` |
| `--heartbeat-interval-seconds` | `heartbeat.interval.seconds` |
| `--tag key=value`（可重复） | `tags` |
| `--app-map pattern=name`（可重复） | `app.name.map` |
| `--help` / `-h` | 帮助 |
| `--list-tools` | 列出工具 |

不常用项建议写在 properties 中；env 键名**未删除**，见附录。

### 1.2 探针示例

```bash
cp java-diag-mcp-server/src/main/resources/java-diag-mcp-server.properties.example \
   ./java-diag-mcp-server.properties
# 编辑 auth.token / register.token（必填）、allowed.log.dirs、register.url 等

java -jar java-diag-mcp-server.jar --config ./java-diag-mcp-server.properties

# 临时覆盖端口
java -jar java-diag-mcp-server.jar --config ./java-diag-mcp-server.properties --port 8766

# 本地 IDE（仍须提供两个 token：文件 / env / CLI）
java -jar java-diag-mcp-server.jar --stdio --token probe-secret --register-token reg-secret
```

---

## 2. 网关 Gateway（`java-diag-gateway`）

Gateway **仅 HTTP**。

### 2.0 Properties 键 ↔ 默认

| Properties 键 | 兼容 env | 默认值 | 含义 |
|---------------|----------|--------|------|
| `http.host` | `JAVA_DIAG_HTTP_HOST` | `0.0.0.0` | 监听地址 |
| `http.port` | `JAVA_DIAG_HTTP_PORT` | `8787` | 监听端口 |
| `auth.token` | `JAVA_DIAG_AUTH_TOKEN` | **必填** | Agent / MCP Bearer；空则启动失败 |
| `heartbeat.ttl.seconds` | `JAVA_DIAG_HEARTBEAT_TTL_SECONDS` | `90` | 探针心跳 TTL（≥5） |
| `instance.id` | `JAVA_DIAG_INSTANCE_ID` | `gateway-{pid}` | Gateway 自身 envelope |
| `audit.path` | `JAVA_DIAG_GATEWAY_AUDIT_PATH` | `./logs/java-diag-gateway-audit.log` | 代理审计 JSONL |
| `register.token` | `JAVA_DIAG_REGISTRY_REGISTER_TOKEN` | **必填** | `/registry/*` Bearer；空则启动失败 |
| `dump.cooldown.ms` | `JAVA_DIAG_DUMP_COOLDOWN_MS` | `10000` | 代理侧 dump 冷却 |
| `admin.user` | `JAVA_DIAG_ADMIN_USER` | `admin` | 控制台用户名 |
| `admin.password` | `JAVA_DIAG_ADMIN_PASSWORD` | 未配置时回退 `java-diag`（本地便利）；示例/部署模板为 `CHANGE_ME_ADMIN_PASSWORD`，共享/生产务必改强密码（弱/占位值启动会 WARNING） | 控制台密码 |

### 2.1 CLI（薄入口）

| CLI | 说明 |
|-----|------|
| `--config <file>` | 配置文件 |
| `--host` / `--port` | HTTP 监听 |
| `--token` / `--auth-token` | Agent token |
| `--register-token` | Registry token |
| `--admin-user` / `--admin-password` | 控制台（建议 properties） |
| `--heartbeat-ttl-seconds` | TTL |
| `--help` / `-h` | 帮助 |

### 2.2 Gateway 示例

```bash
java -jar java-diag-gateway.jar --config ./java-diag-gateway.properties
java -jar java-diag-gateway.jar --config ./gw.properties --token agent-override
```

端点、`diag.list_instances` / `diag.resolve_service` 等行为未变，见历史版本文档或源码。

---

## 3. 附录：环境变量速查（高级覆盖）

与 properties 点分键一一对应；**同一项 env 覆盖文件，CLI 覆盖 env**。

### Probe

`JAVA_DIAG_TRANSPORT` · `JAVA_DIAG_HTTP_HOST` · `JAVA_DIAG_HTTP_PORT` · `JAVA_DIAG_AUTH_TOKEN` · `JAVA_DIAG_ALLOWED_LOG_DIRS` · `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX` · `JAVA_DIAG_TOOL_TIMEOUT_MS` · `JAVA_DIAG_MAX_OUTPUT_BYTES` · `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` · `JAVA_DIAG_AUDIT_PATH` · `JAVA_DIAG_DUMP_COOLDOWN_MS` · `JAVA_DIAG_DUMP_TIMEOUT_MS` · `JAVA_DIAG_SERVICE_NAME` · `JAVA_DIAG_TAGS` · `JAVA_DIAG_REGISTER_URL` · `JAVA_DIAG_REGISTRY_REGISTER_TOKEN` · `JAVA_DIAG_ADVERTISE_URL` · `JAVA_DIAG_HEARTBEAT_INTERVAL_SECONDS` · `JAVA_DIAG_APP_NAME_MAP`

### Gateway

`JAVA_DIAG_HTTP_HOST` · `JAVA_DIAG_HTTP_PORT` · `JAVA_DIAG_AUTH_TOKEN` · `JAVA_DIAG_GATEWAY_AUDIT_PATH` · `JAVA_DIAG_DUMP_COOLDOWN_MS` · `JAVA_DIAG_REGISTRY_REGISTER_TOKEN` · `JAVA_DIAG_HEARTBEAT_TTL_SECONDS` · `JAVA_DIAG_INSTANCE_ID` · `JAVA_DIAG_ADMIN_USER` · `JAVA_DIAG_ADMIN_PASSWORD`

> Probe 与 Gateway 共用部分 env 名（`JAVA_DIAG_HTTP_*`、`JAVA_DIAG_AUTH_TOKEN`）。分进程启动时各自设置即可。

---

## 4. 源码锚点

| 组件 | 类 |
|------|-----|
| Probe 配置 | `java-diag-mcp-server/.../config/DiagConfig.java` |
| Probe properties 加载 | `.../config/SettingsProperties.java` |
| Probe 入口 | `.../JavaDiagMcpServer.java` |
| Gateway 配置 | `java-diag-gateway/.../config/GatewayConfig.java` |
| Gateway properties 加载 | `.../config/SettingsProperties.java` |
| Gateway 入口 | `.../JavaDiagGateway.java` |
