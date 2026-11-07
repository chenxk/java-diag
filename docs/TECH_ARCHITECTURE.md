# Java 线上排查助手 — 技术架构设计方案

> 文档用途：交给 Codex / 编码 Agent 按模块实现。  
> 产品代号：`java-diag`（可改名）  
> 版本：v0.1  
> 日期：2026-11-07

---

## 1. 背景与目标

### 1.1 要解决的问题

生产 Java 故障（OOM、CPU 飙高、线程池打满、死锁、Full GC、接口超时）排查时：

- 日志与 JVM 证据分散在多台机器 / 多 Pod
- 人肉 `jcmd` / `jstack` / `tail` 慢，且难交叉推理
- Agent（Cursor / Claude 等）需要标准 MCP 工具口，而不是让模型直接乱写 shell

### 1.2 产品目标

做一个 **只读、可审计** 的 Java 事故副驾驶：

- **单机版（开源）**：本机一个 MCP Server，暴露日志 + JVM 采集工具
- **集群版（收费）**：多节点探针 + 注册发现 + 机器路由，Agent 只连一个入口即可查任意节点

### 1.3 非目标（MVP 明确不做）

- 不做成 Arthas 远程执行 / 任意命令执行平台
- 不做公网 SaaS（优先私有化 / 客户内网）
- 不做完整 CMDB / JumpServer 替代（机器权限以后再接）
- 不做自动改代码 / 自动重启（可建议，不执行）
- 首期不做 Windows 宿主（Linux / 容器优先）

---

## 2. 产品分版与商业边界

| 能力 | 单机版（OSS） | 集群版（商业） |
|------|---------------|----------------|
| 本地 MCP Server | ✅ | ✅（含） |
| 日志只读工具 | ✅ | ✅ |
| JVM 只读工具（jcmd/jstat/jstack/histo…） | ✅ | ✅ |
| 结构化 JSON 输出 | ✅ | ✅ |
| 本地审计日志 | ✅（简版） | ✅（完整） |
| 多节点探针 | ❌ | ✅ |
| 服务发现 / 路由 | ❌ | ✅ |
| Nacos MCP Registry 对接 | ❌（可选社区贡献） | ✅ |
| 内置轻量 Registry | ❌ | ✅ |
| 标签路由 / 按服务名选节点 | ❌ | ✅ |
| 集中审计 / RBAC / License | ❌ | ✅ |

**实现原则**：探针工具集 **一套代码**；分版差异只在「如何被发现、如何路由、是否启用集群模块」。

License 建议：

- 单机核心：`Apache-2.0` 或 `MIT`
- 集群模块：独立私有仓库 / 商业 License（`java-diag-cluster`）
- 用模块边界硬隔离，避免开源污染

---

## 3. 总体架构

### 3.1 单机版

```
Agent (Cursor / Claude / 自研)
        │ MCP (stdio 或 SSE)
        ▼
┌───────────────────────────┐
│  java-diag-mcp (单进程)   │
│  - Tool Registry          │
│  - Log tools              │
│  - JVM tools              │
│  - Local audit            │
└───────────────────────────┘
        │
        ▼
   本机进程 / 日志文件 / JDK 工具
```

### 3.2 集群版

```
Agent
  │ MCP（只连一个入口）
  ▼
┌─────────────────────────────────────┐
│  java-diag-gateway（集群入口）       │
│  - MCP Router / Tool Proxy          │
│  - Registry Client (SPI)            │
│  - 路由：service / tag / instance    │
│  - 审计 / 鉴权 / 限流                │
└──────────────┬──────────────────────┘
               │ 发现
     ┌─────────┴─────────┐
     ▼                   ▼
 Nacos MCP Registry   Built-in Registry
 (3.x 可用时)         (无 Nacos / 低版本)
     │                   │
     └─────────┬─────────┘
               │ 调用目标节点 MCP
               ▼
     ┌─────────────────────┐
     │ diag-probe × N      │  ← 与单机版同一套工具实现
     │ (每机/每 Pod 一个)   │
     └─────────────────────┘
```

**核心链路（集群）**：`Agent → Gateway → Registry 选节点 → Probe MCP Tools`

JumpServer **非必需**；MVP 用标签 / 服务名做发现即可。

---

## 4. 模块划分（Maven / Gradle 多模块）

建议仓库结构：

```
java-diag/
├── java-diag-common          # 模型、错误码、JSON schema、审计事件
├── java-diag-probe-core      # 工具实现（日志/JVM），单机与集群共用
├── java-diag-mcp-server      # MCP Server 宿主（stdio/SSE），单机可执行入口
├── java-diag-registry-api    # Registry SPI（接口 + DTO）
├── java-diag-registry-nacos  # Nacos 实现（集群）
├── java-diag-registry-local  # 内置轻量注册中心（集群）
├── java-diag-gateway         # 集群 MCP 路由入口（商业）
├── java-diag-probe-agent     # 探针进程打包（可含轻量注册客户端）
└── docs/
    └── TECH_ARCHITECTURE.md  # 本文档
```

开源发布：`common` + `probe-core` + `mcp-server`  
商业发布：上述 + `registry-*` + `gateway` + `probe-agent` 集群能力

---

## 5. 技术选型

| 层 | 选型 | 说明 |
|----|------|------|
| 语言 | Java 17+ | 与目标用户栈一致；探针需调本机 JDK 工具 |
| MCP SDK | 官方 MCP Java SDK 或 Spring AI Alibaba MCP | 优先选社区活跃、能注册 tools 的 |
| 传输 | stdio（单机 IDE）、SSE/HTTP（集群） | Gateway 必须 HTTP/SSE |
| 序列化 | JSON | 工具出入参全部结构化 |
| 注册 | Nacos 3.x MCP Registry；自研 Built-in | 通过 SPI 切换 |
| 构建 | Maven 或 Gradle（二选一，全仓统一） | Codex 实现时固定一种 |
| 打包 | 单 jar / 容器镜像 | probe 镜像要小，含（或依赖）目标机 JDK 工具 |
| 观测 | Micrometer + 本地 audit log | 集群可接 OTLP（后期） |

**约束**：探针侧调用外部进程（`jcmd`/`jstack` 等）必须超时、输出大小上限、白名单参数。

---

## 6. Registry SPI（集群关键抽象）

### 6.1 接口（`java-diag-registry-api`）

```java
public interface Registry {
  void register(ProbeInstance instance);
  void deregister(String instanceId);
  void heartbeat(String instanceId);
  List<ProbeInstance> list(ProbeQuery query);
  Optional<ProbeInstance> resolve(RouteRequest request);
}

public record ProbeInstance(
  String instanceId,
  String serviceName,
  String host,
  int port,
  String mcpEndpoint,          // e.g. http://ip:port/sse
  Map<String, String> tags,    // env=prod, app=order, zone=hz
  String version,
  Instant lastHeartbeat,
  ProbeStatus status
) {}

public record ProbeQuery(
  String serviceName,
  Map<String, String> tagEquals,
  boolean healthyOnly
) {}

public record RouteRequest(
  String serviceName,
  String instanceId,           // 可选，精确指定
  Map<String, String> tagEquals,
  RouteStrategy strategy       // FIRST / RANDOM / CONSISTENT_HASH
) {}
```

### 6.2 实现 A：Nacos

- 适用：客户已有 **Nacos 3.x** 且支持 MCP Registry
- 探针启动：将 MCP server 元数据（tools 描述、endpoint、tags）注册到 Nacos
- Gateway：通过 Nacos MCP Router 能力或自研查询 + 转发
- 降级：Nacos 不可用 → 明确错误，不静默切内置（可配置 `fallback-to-local=true`）

### 6.3 实现 B：Built-in 轻量 Registry

- 适用：无 Nacos / 版本过低 / 不想依赖 Nacos
- 组件：`registry-local` 单进程（可与 Gateway 同进程部署）
- 能力最小集：
  - HTTP API：`POST /register`、`POST /heartbeat`、`DELETE /deregister`、`GET /instances`
  - 内存 + 可选本地文件/SQLite 持久化
  - TTL 心跳（默认 30s，超时 90s 标记不健康）
  - 按 `serviceName` + tags 过滤
- **不做**：配置中心、动态配置推送、复杂一致性协议（MVP 单节点即可；HA 后期用 Raft/外置 DB）

### 6.4 配置切换

```yaml
java-diag:
  registry:
    type: nacos | local
    nacos:
      server-addr: ...
      namespace: java-diag
    local:
      url: http://registry:8081
      heartbeat-interval: 15s
      ttl: 90s
```

---

## 7. MCP 工具集（单机 / 探针共用）

> 所有工具：**只读**、有超时、有输出上限、写审计。  
> 命名建议：`diag.*` 前缀。

### 7.1 元信息

| Tool | 入参 | 出参要点 |
|------|------|----------|
| `diag.ping` | — | version, host, pid, javaHome |
| `diag.list_processes` | keyword?, user? | pid, mainClass, startTime, rss? |
| `diag.resolve_java_pid` | mainClass / jar / port | 匹配到的 pid 列表 |

### 7.2 日志

| Tool | 入参 | 约束 |
|------|------|------|
| `diag.log_paths` | pid? / app? | 推断常见日志路径，不 recursive 乱扫 |
| `diag.tail_log` | path, lines≤500, follow=false | 路径白名单 / 禁止 `..` |
| `diag.grep_log` | path, pattern, since, maxMatches≤200 | 超时 10s，结果截断 |
| `diag.cluster_errors` | path, window=15m | 异常类名聚类 + top N |

### 7.3 JVM

| Tool | 入参 | 说明 |
|------|------|------|
| `diag.jstat` | pid, option=gc/gccapacity..., interval?, count? | 包装 jstat，限制 count |
| `diag.thread_dump` | pid, maxBytes | jstack/jcmd Thread.print |
| `diag.vm_flags` | pid | 只读 flags 摘要 |
| `diag.gc_histo` | pid, live?=false | histo，默认不强制 full GC |
| `diag.runtime_summary` | pid | 堆/元空间/线程数/uptime 摘要 |

### 7.4 集群 Gateway 额外工具

| Tool | 说明 |
|------|------|
| `diag.list_instances` | 列出 Registry 中探针 |
| `diag.route` | 按 service/tag 选实例（也可隐式：每个工具带 `target`） |

**目标寻址约定（集群）**：每个探针工具增加可选参数：

```json
{
  "target": {
    "serviceName": "order-service",
    "instanceId": optional,
    "tags": {"env": "prod"}
  },
  "...原工具参数"
}
```

Gateway 负责 resolve → 转发到对应 probe；单机版忽略 `target`。

### 7.5 明确禁止的工具（MVP）

- 任意 `exec` / shell
- `heapdump` 默认关闭（可配置 `dangerousToolsEnabled=false`；开启需二次确认字段）
- 修改 JVM flags、kill -9、删日志

---

## 8. 安全、审计与限流

### 8.1 只读与沙箱

- 路径：允许目录列表配置 `allowedLogDirs`
- 进程：可选 `allowedPids` / `allowedMainClassRegex`
- 命令：固定模板拼装，禁止用户传入裸 shell
- 超时：默认 5–15s；dump 类 30s
- 输出：默认 max 256KB，可配置

### 8.2 鉴权（集群）

- Gateway：Bearer Token / mTLS（二选一先做 Token）
- Probe：只接受来自 Gateway 的调用（shared secret 或 mTLS）
- 单机 stdio：默认信任本地 Agent（可选 token）

### 8.3 审计事件

每条工具调用记录：

```json
{
  "ts": "...",
  "actor": "token-id-or-user",
  "tool": "diag.thread_dump",
  "targetInstance": "...",
  "argsDigest": "sha256(...)",
  "durationMs": 123,
  "success": true,
  "errorCode": null
}
```

单机：本地 rolling file  
集群：Gateway 汇总 + 各 probe 本地副本

### 8.4 限流

- 每实例每工具 QPS / 并发上限（防止连续 thread dump 打挂进程）
- `thread_dump` / `gc_histo` 单独更严的冷却时间

---

## 9. 部署形态

### 9.1 单机版

```bash
# IDE / Claude Desktop MCP 配置示例
{
  "mcpServers": {
    "java-diag": {
      "command": "java",
      "args": ["-jar", "/opt/java-diag/java-diag-mcp-server.jar"],
      "env": {
        "JAVA_DIAG_ALLOWED_LOG_DIRS": "/var/log/app,/home/app/logs"
      }
    }
  }
}
```

### 9.2 集群版

1. 每台业务机 / 每个 Pod sidecar 跑 `diag-probe`
2. 部署 1 个 `gateway`（可多副本，无状态；Registry 视实现）
3. `registry.type=nacos` 或 `local`
4. Agent 只配置 Gateway 的 MCP SSE 地址

K8s 建议：DaemonSet 或 sidecar；标签同步为 Registry tags（`app.kubernetes.io/name` 等）。

---

## 10. 关键时序

### 10.1 单机调用

```
Agent → MCP tool call(diag.thread_dump)
     → mcp-server 校验参数
     → probe-core 执行 jcmd
     → 写 audit
     → 返回 JSON
```

### 10.2 集群调用

```
Agent → Gateway.diag.thread_dump(target={service,tags}, pid=...)
     → Registry.resolve(...)
     → HTTP/SSE 转发到 Probe
     → Probe 执行并返回
     → Gateway 写审计并回传 Agent
```

### 10.3 探针注册

```
Probe 启动 → 读配置 tags/serviceName
          → Registry.register(instance)
          → 定时 heartbeat
          → 优雅退出 deregister
```

---

## 11. 数据模型（工具统一响应信封）

```json
{
  "ok": true,
  "tool": "diag.thread_dump",
  "instanceId": "host-a-1",
  "tookMs": 842,
  "truncated": false,
  "data": { },
  "error": null
}
```

错误码（`java-diag-common`）：

- `INVALID_ARGUMENT`
- `PID_NOT_FOUND`
- `PATH_NOT_ALLOWED`
- `TIMEOUT`
- `OUTPUT_TRUNCATED`（ok=true 也可带 truncated）
- `INSTANCE_NOT_FOUND`
- `REGISTRY_UNAVAILABLE`
- `RATE_LIMITED`
- `UNAUTHORIZED`
- `INTERNAL`

---

## 12. 配置项清单（实现时落成 `application.yml` / env）

**Probe / 单机**

- `java-diag.bind` / transport
- `java-diag.allowedLogDirs`
- `java-diag.allowedMainClassRegex`
- `java-diag.toolTimeoutMs`
- `java-diag.maxOutputBytes`
- `java-diag.dangerousToolsEnabled`（默认 false）
- `java-diag.audit.path`

**集群 Probe 额外**

- `java-diag.serviceName`
- `java-diag.tags.*`
- `java-diag.registry.type`
- `java-diag.gatewaySharedSecret`

**Gateway**

- `java-diag.registry.*`
- `java-diag.auth.token`
- `java-diag.rateLimit.*`

---

## 13. 测试与验收（给 Codex 的完成标准）

### 13.1 单机 MVP 验收

1. 能以 stdio 启动 MCP，被 MCP Inspector / 兼容客户端列出全部 `diag.*` 工具
2. 对示例 Spring Boot 进程：`list_processes` → `runtime_summary` → `thread_dump` 跑通
3. `grep_log` 在白名单目录外返回 `PATH_NOT_ALLOWED`
4. 超时与截断行为可单测
5. 审计文件有对应记录
6. 无任意 shell 入口

### 13.2 集群 MVP 验收

1. 2 个 probe + local registry + gateway 本地 docker-compose 跑通
2. Agent 只连 gateway，能 `list_instances` 并按 tag 命中节点执行 `thread_dump`
3. 停掉一个 probe，心跳过期后不再被路由到
4. 切换 `registry.type=nacos`（有环境时）注册与发现成功；无 Nacos 时文档说明用 local
5. 商业模块不出现在 OSS 构建产物中

---

## 14. 迭代路线

| 阶段 | 交付 | 预估 |
|------|------|------|
| P0 | 单机 MCP：进程/日志/JVM 核心工具 + 审计 + 安全约束 | 先做 |
| P1 | Registry SPI + **local** 实现 + Gateway 路由 | 集群最小可用 |
| P2 | Nacos Registry 实现 + 文档 | 覆盖有 Nacos 客户 |
| P3 | License / RBAC / 危险工具审批流 / heapdump 受控开关 | 商业化 |
| P4 | K8s 清单、Helm、告警接入剧本（Playbook） | 规模化 |

**推荐开发顺序（给 Codex）**：

1. `common` + `probe-core` 工具与单测  
2. `mcp-server` 单机可运行  
3. `registry-api` + `registry-local`  
4. `gateway` 转发  
5. `registry-nacos`  
6. 打包、compose、文档  

---

## 15. 给 Codex 的实现约束（必须遵守）

1. **不要**实现通用 shell/exec 工具  
2. 所有外部进程调用必须：白名单命令 + ArgumentList（非拼接字符串）+ timeout + max bytes  
3. 单机与集群 **共用** `probe-core`，禁止复制两套工具逻辑  
4. Registry 只能通过 SPI，禁止在 gateway 写死 Nacos API  
5. 默认配置必须安全（危险能力关闭）  
6. 先写接口与单测，再写 Nacos 集成  
7. 代码与注释主语言：中文可出现在文档；代码标识符用英文  
8. 每完成一个模块，更新 `docs/DEV_STATUS.md` 列出已完成工具与未完成项  

---

## 16. 附录：典型排查剧本（产品验收场景）

**剧本 A：接口超时**

1. `list_instances`(service=order, env=prod)  
2. `grep_log` / `cluster_errors`（15m）  
3. 若线程可疑 → `thread_dump`  
4. 输出：时间线 + 证据 + 假设 + 是否需要 heapdump（仅建议）

**剧本 B：内存飙升**

1. `runtime_summary` + `jstat(gc)`  
2. `gc_histo`  
3. 关联日志 OOM / GC 关键字  

**剧本 C：单机开发者本机**

1. 只启 `mcp-server`  
2. IDE Agent 直接调用，无 Registry  

---

## 17. 开放决策（已拍板）

| 决策 | 结论 |
|------|------|
| JumpServer | MVP 不做 |
| 双 Registry | Nacos + 内置轻量，SPI 切换 |
| 分版 | 单机 OSS，集群收费 |
| 工具策略 | 一套探针，差别在发现/路由 |
| 安全默认 | 只读、审计、无任意执行 |

---

*End of design. Codex 应从 P0 `probe-core` + `mcp-server` 开始实现。*
