# DEV_STATUS

更新日期：2026-11-07

## 仓库形态

Maven 父工程 + 两模块：

- `java-diag-mcp-server` — 探针（HTTP/stdio MCP + 可选心跳注册）
- `java-diag-gateway` — 集群 Gateway（内置 Registry + 工具代理）

## P0 探针工具（12）

`diag.ping` … `diag.gc_histo` — 已完成（只读、审计、白名单、超时截断）

## P1 集群 MVP

| 项 | 状态 |
|----|------|
| 独立 gateway 包 | ✅ |
| Built-in Registry（register/heartbeat/TTL） | ✅ |
| Registry 无 register-token（信任内网） | ✅ |
| 探针 `--register-url` 心跳客户端 | ✅ |
| `diag.list_instances` | ✅ |
| `target` 代理；多命中返回 candidates | ✅ |
| 探针无独立 instance-id；身份 = advertise host:port | ✅ |
| 允许 Agent 直连探针 | ✅（设计 + 行为） |
| Nacos | ❌ P2 |

## 文档

- [CONFIGURATION.md](CONFIGURATION.md) — 探针 / 网关全部配置项（env + CLI）

## 验证

```bash
mvn -q clean test package
```
