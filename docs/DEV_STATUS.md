# DEV_STATUS

更新日期：2026-11-07

## 仓库形态

Maven 父工程 + 两模块：

- `java-diag-mcp-server` — 探针（HTTP/stdio MCP + 可选心跳注册）
- `java-diag-gateway` — 集群 Gateway（内置 Registry + 工具代理）

## 探针 MCP 工具（15）

P0（12）：`diag.ping` … `diag.gc_histo` — 只读、审计、白名单、超时截断  
新增（Top5 #1）：`diag.heap_dump`、`diag.cpu_sample`（`diag.vm_info` 已存在）

| 能力 | 说明 |
|------|------|
| 结构化摘要 | `runtime_summary` / `thread_dump` / `jstat`（`gcutil`）返回 `summary` 字段（`JvmOutputParser`） |
| `diag.heap_dump` | `JAVA_DIAG_DANGEROUS_TOOLS_ENABLED` + `path` 须在 `allowedLogDirs` 且 `.hprof` |
| `diag.cpu_sample` | 两次 `Thread.print` 间隔 `pauseMs`，返回 `delta`（非 async-profiler） |

## 跨平台

| 能力 | Linux | macOS | Windows |
|------|-------|-------|---------|
| 进程发现 | `/proc`，失败回退 `jps` / `ProcessHandle` | `jps` → `ProcessHandle` | `jps` → `ProcessHandle` |
| JDK 工具 | `jcmd`/`jps`/… | 同左 | `*.exe` + `PATH` 用 `;` |
| 默认日志白名单 | `.` / `$HOME` / tmp / `/var/log` | 同左 | `.` / `%USERPROFILE%` / `%TEMP%` |

## P1 集群 MVP

| 项 | 状态 |
|----|------|
| 独立 gateway 包 | ✅ |
| Built-in Registry（register/heartbeat/TTL） | ✅ |
| Gateway `/admin/` 注册表控制台（session 登录 + 只读 API；Tab/探针 MCP 弹窗复制） | ✅ |
| Registry 可选 register-token（默认信任内网） | ✅ Top5 |
| Gateway JSONL 代理审计 | ✅ Top5 |
| 探针 + Gateway dump 冷却 `RATE_LIMITED` | ✅ Top5 |
| 探针 `--register-url` 心跳客户端 | ✅ |
| `diag.list_instances` | ✅ |
| 心跳上报本机 `apps[]`（JVM 目录） | ✅ |
| `diag.resolve_service`（业务名→探针+pid） | ✅ |
| `list_instances` / Admin 按 app 过滤 | ✅ |
| `target` 代理；多命中返回 candidates | ✅ |
| 代理新工具 `heap_dump` / `cpu_sample` / `vm_info` | ✅ Top5 |
| grep/cluster `since` 时间过滤；log_paths cmdline 推断 | ✅ Top5 |
| 探针无独立 instance-id；身份 = advertise host:port | ✅ |
| 允许 Agent 直连探针 | ✅（设计 + 行为） |
| 注册发现 | ✅ 仅 Gateway 内嵌 Registry（不对接第三方注册中心） |

## 文档

- [CONFIGURATION.md](CONFIGURATION.md) — 探针 / 网关全部配置项（env + CLI）

## 验证

```bash
# Windows（IntelliJ 捆绑 Maven）
"C:\Program Files\JetBrains\IntelliJ IDEA 2024.1\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -q clean test package
```