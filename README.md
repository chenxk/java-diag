# java-diag

只读、可审计的 Java 线上排查 MCP Server（单机 P0）。通过 stdio 向 Cursor / Claude 等 Agent 暴露 `diag.*` 工具，采集本机进程、日志与 JVM 证据；**不提供**通用 shell/exec。

## 要求

- JDK 17+（构建与运行）
- Linux（首期不做 Windows）
- Maven 3.8+（仅构建需要）

## 构建

```bash
mvn -q clean package
```

产物：`target/java-diag-mcp-server.jar`（fat jar）

列出工具（不进入 MCP 会话）：

```bash
java -jar target/java-diag-mcp-server.jar --list-tools
```

## 运行（stdio MCP）

```bash
java -jar target/java-diag-mcp-server.jar
```

常用环境变量：

| 变量 | 含义 | 默认 |
|------|------|------|
| `JAVA_DIAG_ALLOWED_LOG_DIRS` | 日志路径白名单（逗号分隔） | `.` / `$HOME` / `/tmp` / `/var/log` |
| `JAVA_DIAG_ALLOWED_MAIN_CLASS_REGEX` | 可选：限制可操作的 main class | 不限制 |
| `JAVA_DIAG_TOOL_TIMEOUT_MS` | 普通工具超时 | `15000` |
| `JAVA_DIAG_MAX_OUTPUT_BYTES` | 输出上限 | `262144` |
| `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` | 允许 `gc_histo(live=true)` | `false` |
| `JAVA_DIAG_AUDIT_PATH` | 审计 JSONL 路径 | `./logs/java-diag-audit.log` |
| `JAVA_DIAG_INSTANCE_ID` | 响应信封 instanceId | `主机名-pid` |

## MCP 客户端配置示例

Cursor / Claude Desktop 等：

```json
{
  "mcpServers": {
    "java-diag": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/java-diag-mcp-server.jar"],
      "env": {
        "JAVA_DIAG_ALLOWED_LOG_DIRS": "/var/log/app,/home/app/logs"
      }
    }
  }
}
```

## P0 工具

**Meta**

- `diag.ping`
- `diag.list_processes`
- `diag.resolve_java_pid`

**Logs**（路径白名单、禁止 `..`、超时与截断）

- `diag.log_paths`
- `diag.tail_log`
- `diag.grep_log`
- `diag.cluster_errors`

**JVM**（优先目标进程 JDK 工具链；`jcmd` 优先）

- `diag.runtime_summary`
- `diag.jstat`
- `diag.thread_dump`
- `diag.vm_flags`
- `diag.gc_histo`（默认不强制 Full GC）

明确不做：通用 exec、heapdump（默认关闭）、gateway/registry/集群、Windows。

## 测试

```bash
mvn -q test
```

## 开发状态

见 [`docs/DEV_STATUS.md`](docs/DEV_STATUS.md)。架构设计见 Agent Store 中的 `TECH_ARCHITECTURE.md`（P0 已改为单模块，不再按多模块布局实现）。

## License

MIT
