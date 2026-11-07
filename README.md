# java-diag

只读、可审计的 Java 线上排查 **MCP Server**。部署在业务机器 / 云主机上，Agent 通过 **IP:端口** 远程连接；本机 IDE 也可用 stdio。

采集本机进程、日志与 JVM 证据；**不提供**通用 shell/exec。

## 要求

- JDK 17+（构建与运行）
- Linux（首期不做 Windows）
- Maven 3.8+（仅构建需要）

## 构建

```bash
mvn -q clean package
```

产物：`target/java-diag-mcp-server.jar`（fat jar）

```bash
java -jar target/java-diag-mcp-server.jar --list-tools
```

## 云端部署（HTTP / IP:端口）— 默认模式

在目标机器启动（推荐用命令行指定 token）：

```bash
export JAVA_DIAG_ALLOWED_LOG_DIRS='/var/log/app,/home/app/logs'
java -jar java-diag-mcp-server.jar --host 0.0.0.0 --port 8765 --token replace-me
```

也可用环境变量 `JAVA_DIAG_AUTH_TOKEN`；若同时提供，**命令行 `--token` / `--auth-token` 优先**。

监听地址默认 `0.0.0.0:8765`。端点：

| 路径 | 用途 |
|------|------|
| `GET /sse` | MCP SSE（常见远程客户端） |
| `POST /mcp/message` | SSE 消息通道 |
| `/mcp` | MCP Streamable HTTP |
| `GET /health` | 探活（不校验 token） |

生产环境务必设置 `JAVA_DIAG_AUTH_TOKEN`，客户端带：

`Authorization: Bearer <token>`

### Agent / Cursor 远程 MCP 配置示例

```json
{
  "mcpServers": {
    "java-diag-prod-box": {
      "url": "http://10.0.1.23:8765/sse",
      "headers": {
        "Authorization": "Bearer replace-me"
      }
    }
  }
}
```

Streamable HTTP 客户端可改用：

```json
{
  "mcpServers": {
    "java-diag-prod-box": {
      "url": "http://10.0.1.23:8765/mcp",
      "headers": {
        "Authorization": "Bearer replace-me"
      }
    }
  }
}
```

安全建议：仅内网 / VPN / 安全组开放端口；配合 token；不要把探针直接暴露公网。

## 本机 IDE（stdio）

```bash
java -jar java-diag-mcp-server.jar --stdio
```

```json
{
  "mcpServers": {
    "java-diag": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/java-diag-mcp-server.jar", "--stdio"],
      "env": {
        "JAVA_DIAG_ALLOWED_LOG_DIRS": "/var/log/app,/home/app/logs"
      }
    }
  }
}
```

## 环境变量

| 变量 | 含义 | 默认 |
|------|------|------|
| `JAVA_DIAG_TRANSPORT` | `http` 或 `stdio` | `http` |
| `JAVA_DIAG_HTTP_HOST` | 绑定地址 | `0.0.0.0` |
| `JAVA_DIAG_HTTP_PORT` | 监听端口 | `8765` |
| `JAVA_DIAG_AUTH_TOKEN` | 远程 Bearer token（可被 `--token` 覆盖） | 空（不推荐） |
| `JAVA_DIAG_ALLOWED_LOG_DIRS` | 日志路径白名单 | `.` / `$HOME` / `/tmp` / `/var/log` |
| `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX` | 可选 main class 限制 | 不限制 |
| `JAVA_DIAG_TOOL_TIMEOUT_MS` | 普通工具超时 | `15000` |
| `JAVA_DIAG_MAX_OUTPUT_BYTES` | 输出上限 | `262144` |
| `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` | 允许 `gc_histo(live=true)` | `false` |
| `JAVA_DIAG_AUDIT_PATH` | 审计 JSONL | `./logs/java-diag-audit.log` |
| `JAVA_DIAG_INSTANCE_ID` | 响应信封 instanceId | `主机名-pid` |

## P0 工具

**Meta:** `diag.ping`, `diag.list_processes`, `diag.resolve_java_pid`  
**Logs:** `diag.log_paths`, `diag.tail_log`, `diag.grep_log`, `diag.cluster_errors`  
**JVM:** `diag.runtime_summary`, `diag.jstat`, `diag.thread_dump`, `diag.vm_flags`, `diag.gc_histo`

明确不做：通用 exec、heapdump（默认关闭）、gateway/registry/集群、Windows。

## 测试

```bash
mvn -q test
```

## 开发状态

见 [`docs/DEV_STATUS.md`](docs/DEV_STATUS.md)。

## License

MIT
