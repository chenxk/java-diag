# DEV_STATUS — java-diag P0

更新日期：2026-11-07

## 布局决策（相对原 TECH_ARCHITECTURE）

- **已落地**：单 Maven 模块 `java-diag-mcp-server`，一个 fat jar
- **已落地**：远程 HTTP MCP（Jetty 嵌入；SSE + Streamable HTTP），Agent 经 IP:端口连接
- **未做**：多模块拆分、gateway、registry、Nacos、集群路由

## 传输

| 模式 | 启动 | 说明 |
|------|------|------|
| HTTP（默认） | `java -jar ... --port 8765` | 云端部署；`/sse`、`/mcp`、`/health` |
| stdio | `java -jar ... --stdio` | 本机 IDE |

可选 `JAVA_DIAG_AUTH_TOKEN`（Bearer）保护远程端点；`/health` 除外。

## 已完成工具

| Tool | 状态 | 备注 |
|------|------|------|
| `diag.ping` | ✅ | version/host/pid/javaHome |
| `diag.list_processes` | ✅ | `/proc` 扫描 |
| `diag.resolve_java_pid` | ✅ | mainClass / jar / port |
| `diag.log_paths` | ✅ | 白名单目录非递归 + `logs/` 一层 |
| `diag.tail_log` | ✅ | lines≤500；`follow` 拒绝 |
| `diag.grep_log` | ✅ | 超时 10s；maxMatches≤200 |
| `diag.cluster_errors` | ✅ | 异常类名聚类 top N |
| `diag.runtime_summary` | ✅ | `jcmd VM.summary` |
| `diag.jstat` | ✅ | option 白名单；count≤20 |
| `diag.thread_dump` | ✅ | 优先 `jcmd Thread.print` |
| `diag.vm_flags` | ✅ | `jcmd VM.flags` |
| `diag.gc_histo` | ✅ | 默认非 live；live 需危险开关 |

## 平台能力

- 统一 JSON 信封：`ok/tool/instanceId/tookMs/truncated/data/error`
- 错误码：`INVALID_ARGUMENT` / `PID_NOT_FOUND` / `PATH_NOT_ALLOWED` / `TIMEOUT` / …
- 审计 JSONL（`JAVA_DIAG_AUDIT_PATH`）
- 外部进程：命令 basename 白名单 + `ArgumentList` + timeout + max bytes
- JDK 工具链：优先目标进程 `JAVA_HOME` / `/proc/<pid>/exe`

## 明确未完成 / 禁用

- `heapdump`（危险工具，P0 不暴露）
- 通用 shell/exec
- gateway / registry / cluster（多机路由仍未做；单机远程 HTTP 已支持）
- Windows 宿主
- `tail_log(follow=true)`
- mTLS（当前为可选 Bearer token）

## 验证

- `mvn -q clean package`（JDK 17 release）
- `java -jar target/java-diag-mcp-server.jar --list-tools`
- HTTP：`/health` 200；无 token 访问 `/sse` → 401；带 Bearer → 200
- 单测：路径拒绝、超时、截断、工具清单、HTTP auth
