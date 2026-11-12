# java-diag — 技术架构（As-Built）

> 描述**当前仓库已实现**的结构与行为，供开发与贡献者对照代码。  
> 配置全集见 [CONFIGURATION.md](CONFIGURATION.md)；实现状态见 [DEV_STATUS.md](DEV_STATUS.md)。  
> 版本：与仓库 `0.1.0-SNAPSHOT` 对齐 · 更新日期：2026-11-10

---

## 1. 背景与目标

### 1.1 要解决的问题

生产 Java 故障（OOM、CPU 飙高、线程池打满、死锁、Full GC、接口超时）排查时：

- 日志与 JVM 证据分散在多台机器 / 多 Pod
- 人肉 `jcmd` / `jstack` / `tail` 慢，且难交叉推理
- Agent（Cursor / Claude 等）需要标准 MCP 工具口，而不是让模型直接乱写 shell

### 1.2 产品目标

做一个 **只读、可审计** 的 Java 事故副驾驶：

- **单机**：本机一个探针（`java-diag-probe`），暴露日志 + JVM 采集工具
- **集群**：多节点探针 + Gateway 心跳注册与工具代理；Agent 只连一个入口

本仓库 **探针 + Gateway 均以 MIT 开源**。「单机 / 集群」指部署形态。

### 1.3 非目标

- 不做 Arthas 式远程执行 / 任意 shell
- 不做公网 SaaS（优先内网 / 私有化）
- 不做完整 CMDB / JumpServer 替代
- 不做自动改代码 / 自动重启（可建议，不执行）
- 不对接第三方注册中心；集群发现与路由 **只使用自研 Gateway**

---

## 2. 能力矩阵

| 能力 | 单机探针 | 探针 + Gateway |
|------|----------|----------------|
| MCP Server（工具执行） | ✅ | ✅（探针执行，网关代理） |
| 日志只读工具 | ✅ | ✅ |
| JVM 只读工具 | ✅ | ✅ |
| 统一 JSON 信封 | ✅ | ✅（可含 `routedInstanceId`） |
| 本地审计 JSONL | ✅ | ✅（探针）+ 网关代理审计 |
| 心跳注册 / `target` 路由 | — | ✅ `InMemoryRegistry`（Gateway 内嵌） |
| `diag.list_instances` / `diag.resolve_service` | — | ✅ |
| Admin 控制台 `/admin/` | — | ✅ |
| 多租户 RBAC | — | ❌ 路线图 |

**原则**：诊断逻辑只在探针本机执行；Gateway 负责发现与 MCP 转发，不跑 jcmd、不读业务日志。

---

## 3. 总体架构

### 3.1 单机

```
Agent (Cursor / IDE)
        │ MCP（HTTP/SSE 或 --stdio）
        ▼
┌─────────────────────────────┐
│  java-diag-probe       │
│  ToolRegistrar + Log/JVM    │
│  PathGuard / Audit / 限流   │
└──────────────┬──────────────┘
               ▼
        本机进程 / 日志 / JDK 工具
```

### 3.2 集群

```
Agent
  │ MCP SSE + Bearer（只连 Gateway）
  ▼
┌──────────────────────────────────────┐
│  java-diag-gateway                   │
│  · MCP Tool Proxy（ProbeMcpClient）  │
│  · InMemoryRegistry（同进程）         │
│  · TargetSelector（target → 探针）   │
│  · Agent 鉴权 / 代理审计 / dump 冷却 │
│  · Admin UI /admin/                  │
└────────────┬─────────────────────────┘
             │ register / heartbeat
             │ callTool → 探针 mcpBaseUrl（SSE）
     ┌───────┴────────┐
     ▼                ▼
 Probe A            Probe B …   （与单机同一套工具）
```

**核心链路**：`Agent → Gateway → Registry 选节点 → 短连接 Probe MCP → 本机采集`

多命中时返回 `candidates`，**不静默选机**。

---

## 4. 仓库与模块（实际）

Maven 父工程 + **两个**可运行模块（无独立 `common` / `probe-core` / `registry-*` 子模块）：

```
java-diag/
├── pom.xml                      # 父 POM，Java 17，依赖管理
├── java-diag-probe/        # 探针
│   └── com.javadiag.probe
│       ├── JavaDiagProbe    # 入口（HTTP 默认 / --stdio）
│       ├── config/              # DiagConfig、properties 加载
│       ├── mcp/ToolRegistrar    # 注册 diag.* 工具
│       ├── tools/               # Meta / Log / Jvm
│       ├── process/             # 进程发现、AppNameMapper
│       ├── platform/            # PathGuard、ProcessRunner、Audit…
│       ├── cluster/             # 心跳客户端、JvmDirectoryBuilder
│       └── http/                # Jetty MCP、Bearer、health
├── java-diag-gateway/           # 网关
│   └── com.javadiag.gateway
│       ├── JavaDiagGateway
│       ├── registry/            # InMemoryRegistry、RegistryServlet、DTO
│       ├── route/TargetSelector
│       ├── proxy/ProbeMcpClient
│       ├── mcp/GatewayToolRegistrar
│       ├── admin/               # 控制台 API + 静态资源
│       └── http/                # Jetty、AgentAuthFilter
├── deploy/                      # 启动脚本、Docker、k8s 草稿（不含 jar）
└── docs/
```

| 模块 | 产物 | 默认端口 |
|------|------|----------|
| `java-diag-probe` | shaded jar | `8765` |
| `java-diag-gateway` | shaded jar | `8787` |

---

## 5. 技术选型（已落地）

| 层 | 选型 | 说明 |
|----|------|------|
| 语言 | Java 17+ | `maven.compiler.release=17` |
| MCP | 官方 MCP Java SDK（`io.modelcontextprotocol.sdk`） | 探针 + 网关共用 |
| HTTP | Jetty 12（EE10 Servlet） | 探针 HTTP 模式与 Gateway |
| 传输 | 探针：`http`（默认）或 `stdio`；Gateway：**仅 HTTP/SSE** | |
| 序列化 | Jackson（经各模块 Json 封装） | 工具出入参结构化 |
| 注册 | Gateway 进程内 `InMemoryRegistry` | 无外置 Registry 服务 |
| 构建 | Maven 多模块 + shade | CI：JDK 17 / 21 |
| 观测 | 本地 audit JSONL | 无 Micrometer/OTLP（尚未做） |

**约束**：外部进程（`jcmd`/`jps`/…）经 `ProcessRunner`：参数列表、超时、输出字节上限；日志路径经 `PathGuard` 白名单。

---

## 6. 注册与发现（Built-in Registry）

### 6.1 现状

- Registry **内嵌在 Gateway**，实现类：`InMemoryRegistry`
- HTTP 控制面：`RegistryServlet` → `/registry/register` | `/heartbeat` | `/deregister` | `/instances`
- 必填 `register.token`（`JAVA_DIAG_REGISTRY_REGISTER_TOKEN`）；空则启动失败 / 写接口 401
- 心跳 TTL 可配（默认 90s）；超时标记不健康 / 不再路由
- 注册发现是 Gateway 的一部分，**不**引入外部注册中心或独立 Registry 进程

### 6.2 探针身份

- **无**独立可配 `instance-id`
- `instanceId` = advertise `mcpBaseUrl` 推导的 `host:port`
- 探针通过 `RegistryHeartbeatClient` 定期 `register`/`heartbeat`，payload 含 `mcpBaseUrl`、`tags`、`apps[]`、可选 `probeToken`

### 6.3 路由（`TargetSelector`）

工具参数中的 `target`：

```json
{
  "target": {
    "instanceId": "10.0.1.11:8765",
    "mcpBaseUrl": "http://10.0.1.11:8765",
    "serviceName": "order-service",
    "tags": { "env": "prod" }
  },
  "pid": 12345
}
```

解析结果：

| 结果 | 行为 |
|------|------|
| 0 个健康探针 | `INSTANCE_NOT_FOUND` |
| 多个 | 返回 `candidates`（公开字段），不转发 |
| 正好 1 个 | `ProbeMcpClient` 短连接该探针 SSE，`callTool` 转发（去掉 `target`） |

另有网关工具 `diag.resolve_service`：按业务名 / tags 解析到 `suggestedTarget`（含 `pid`）或 `candidates`。

---

## 7. MCP 工具集

> 均只读（`heap_dump` 等危险能力默认关闭）；有超时 / 截断 / 审计。

### 7.1 探针（`java-diag-probe`，15 个）

**Meta**

| Tool | 要点 |
|------|------|
| `diag.ping` | version、host、pid、javaHome |
| `diag.list_processes` | keyword? / user? |
| `diag.resolve_java_pid` | mainClass / jar / port |

**Logs**

| Tool | 要点 |
|------|------|
| `diag.log_paths` | app? / pid?；扫 `allowedLogDirs`（+ 一级 `logs/`），cmdline 推断 |
| `diag.tail_log` | path，lines≤500，`follow` 不支持 |
| `diag.grep_log` | path、pattern、since?、maxMatches≤200；约 10s 超时 |
| `diag.cluster_errors` | path、window（默认 15m）或 since；异常类聚类 |

**JVM**

| Tool | 要点 |
|------|------|
| `diag.runtime_summary` | jcmd VM.summary + `summary` 结构化字段 |
| `diag.jstat` | option / interval / count（有上限） |
| `diag.thread_dump` | jcmd Thread.print / jstack |
| `diag.vm_flags` | 只读 |
| `diag.gc_histo` | 默认不强制 live/full GC |
| `diag.vm_info` | VM.info 等只读信息 |
| `diag.heap_dump` | 需 `dangerous.tools.enabled`；路径在白名单且 `.hprof` |
| `diag.cpu_sample` | 两次 Thread.print + delta（非 async-profiler） |

### 7.2 Gateway 自有 + 代理

| Tool | 说明 |
|------|------|
| `diag.list_instances` | 列 Registry；可按 serviceName / app 过滤 |
| `diag.resolve_service` | 业务名 → 探针 + pid |
| 上述全部探针工具 | 经代理；入参增加可选 `target` |

> 早期设计中的独立 `diag.route` **未实现**；选机靠 `target` / `resolve_service` / `list_instances`。

### 7.3 禁止

- 任意 `exec` / shell
- 默认关闭的危险能力（未开开关时 `TOOL_DISABLED`）
- 改 JVM flags、kill、删日志

---

## 8. 安全、审计与限流

### 8.1 只读与沙箱

- `allowed.log.dirs`（`PathGuard`，禁止 `..` 逃逸）
- 可选 `allowed.main.class.regex`
- JDK 工具经固定命令模板 + `ProcessRunner`
- `tool.timeout.ms`（默认 15s）、dump 类 `dump.timeout.ms`（默认 30s）
- `max.output.bytes`（默认 256KiB）

### 8.2 鉴权

| 通道 | 机制 |
|------|------|
| Agent → Gateway / 探针 MCP | Bearer（`auth.token`）；必填，空则启动失败 |
| 探针 → Gateway `/registry/*` | Bearer（`register.token`）；必填，空则启动失败；未配置时写接口 401 |
| Admin `/admin/` | 独立用户名密码（session），与 Agent token 分离 |
| 探针 `--stdio` | 默认信任本地进程 |

Gateway 转发探针时可带上心跳上报的 `probeToken`。

### 8.3 审计

探针 / 网关均为 **JSON Lines** 追加文件。字段大致包括：`ts`、`actor`、`tool`、`targetInstance`（网关侧）、`argsDigest`（sha256）、`durationMs`、`success`、`errorCode`。

### 8.4 限流

- 探针与 Gateway 均有 `dump.cooldown.ms`（默认 10s）
- 冷却期内昂贵工具返回 `RATE_LIMITED`

---

## 9. 部署形态

### 9.1 单机

- HTTP：`java -jar java-diag-probe.jar --config ...`（Agent 配 `http://host:8765/sse`）
- 或 `--stdio` 供本地 IDE 拉起

### 9.2 集群

1. 启动 Gateway（`:8787`）
2. 每机 / 每 Pod 启动探针：`--register-url` + `--advertise-url`（集群内 Gateway 可达）
3. Agent 只配 Gateway SSE + Agent Bearer
4. K8s：见 [`deploy/k8s/`](../deploy/k8s/README.md)（Sidecar + `shareProcessNamespace` 推荐）

Docker 草稿：`deploy/Dockerfile.probe` / `Dockerfile.gateway`。

---

## 10. 关键时序

### 10.1 单机

```
Agent → callTool(diag.thread_dump)
     → ToolRegistrar / ToolSupport 校验
     → JvmTools → jcmd/jstack
     → AuditLogger
     → ToolEnvelope JSON
```

### 10.2 集群

```
Agent → Gateway.callTool(diag.thread_dump, target=…, pid=…)
     → TargetSelector.resolve
     →（唯一）ProbeMcpClient → 探针 SSE callTool
     → Gateway 审计 + 回传（可含 routedInstanceId）
```

### 10.3 注册

```
Probe 启动 → RegistryHeartbeatClient
          → POST /registry/register|heartbeat（含 apps[]）
          → TTL 内保持 HEALTHY
```

---

## 11. 数据模型

### 11.1 工具响应信封（`ToolEnvelope`）

```json
{
  "ok": true,
  "tool": "diag.thread_dump",
  "instanceId": "probe-local-or-host",
  "tookMs": 842,
  "truncated": false,
  "data": { },
  "error": null
}
```

失败时 `error: { "code": "PID_NOT_FOUND", "message": "..." }`。  
经 Gateway 代理时，结果中可出现 `routedInstanceId`（实际命中的探针）。

部分 JVM 工具在 `data` 内额外提供 `summary`（`JvmOutputParser`）。

### 11.2 错误码（`ErrorCode`）

`INVALID_ARGUMENT` · `PID_NOT_FOUND` · `PATH_NOT_ALLOWED` · `TIMEOUT` · `OUTPUT_TRUNCATED` · `INSTANCE_NOT_FOUND` · `REGISTRY_UNAVAILABLE` · `RATE_LIMITED` · `UNAUTHORIZED` · `COMMAND_NOT_ALLOWED` · `TOOL_DISABLED` · `INTERNAL`

### 11.3 Registry：`ProbeInstance`（要点）

| 字段 | 含义 |
|------|------|
| `instanceId` | advertise `host:port` |
| `serviceName` | 逻辑服务名 |
| `host` / `port` | 解析自 mcpBaseUrl |
| `mcpBaseUrl` / `ssePath` / `mcpPath` | 网关回调探针 MCP |
| `probeToken` | 可选；Admin 可见，公钥列表会脱敏策略见代码 |
| `tags` | `env=prod` 等 |
| `apps[]` | 心跳上报的本机 JVM 目录 |
| `version` / `lastHeartbeat` / `status` | 元数据与健康 |

### 11.4 `apps[]` 元素（`ProbeJvmApp`）

`name`（可经 `app.name.map` 映射）、`pid`、`mainClass`、`displayName`、`commandLineSummary`、`startTimeEpochMs`、`ports`。

由探针 `JvmDirectoryBuilder` + `JavaProcessService` / `AppNameMapper` 生成。

---

## 12. 配置模型

**不是** Spring `application.yml`。加载顺序：

1. 代码内置默认  
2. properties 文件（无 `--config`：classpath 同名资源，再 cwd 文件，再向上最多 6 层的 `*.properties.local`；`--config` 替换该文件层）  
3. 环境变量 `JAVA_DIAG_*`  
4. CLI（最高）

完整键表见 [CONFIGURATION.md](CONFIGURATION.md)。常用项：

**探针**：`transport`、`http.*`、`auth.token`、`allowed.log.dirs`、`tool.timeout.ms`、`max.output.bytes`、`dangerous.tools.enabled`、`audit.path`、`dump.cooldown.ms`、`service.name`、`register.url`、`advertise.url`、`tags`、`app.name.map`

**Gateway**：`http.*`、`auth.token`、`register.token`、`heartbeat.ttl.seconds`、`audit.path`、`dump.cooldown.ms`、`admin.user` / `admin.password`

---

## 13. 测试与验收（当前标准）

### 13.1 探针

1. HTTP 或 stdio 可列出全部 `diag.*` 工具  
2. `list_processes` → `runtime_summary` → `thread_dump` 在真实 JVM 上可跑通  
3. 白名单外路径 → `PATH_NOT_ALLOWED`  
4. 超时 / 截断 / 审计有单测或可观测行为  
5. 无任意 shell 工具入口  

### 13.2 集群 MVP

1. Gateway + ≥1 探针心跳注册，Admin / `list_instances` 可见  
2. Agent 只连 Gateway，带 `target` 代理 `thread_dump` 等  
3. 心跳过期后不再路由到该实例  
4. 多副本返回 `candidates`  
5. 危险工具默认关闭  

自动化：`mvn test`；CI 见 `.github/workflows/ci.yml`。

---

## 14. 迭代路线

| 阶段 | 交付 | 状态 |
|------|------|------|
| P0 | 单机探针：进程/日志/JVM + 审计 + 安全约束 | ✅ |
| P1 | Gateway + InMemoryRegistry + target 代理 + Admin | ✅ MVP |
| P2 | RBAC / 危险工具审批流 | 路线图 |
| P3 | K8s/Helm 完善、标准排查剧本 | 进行中（`deploy/k8s`） |

---

## 15. 实现约束（持续有效）

1. **不要**增加通用 shell/exec 工具  
2. 外部进程：白名单命令 + 参数列表 + timeout + max bytes  
3. 诊断逻辑保留在探针模块；Gateway 只做注册与代理  
4. 默认配置安全（危险能力关闭）  
5. 标识符英文；用户文档可用中文  
6. 行为变更时同步更新 `CONFIGURATION.md` / `DEV_STATUS.md` / 本文件  

---

## 16. 典型排查剧本

**A. 接口超时（集群）**

1. `diag.resolve_service` 或 `diag.list_instances`  
2. 选定 `target` 后 `diag.grep_log` / `diag.cluster_errors`  
3. 可疑则 `diag.thread_dump` / `diag.cpu_sample`  

**B. 内存飙升**

1. `diag.runtime_summary` + `diag.jstat`  
2. `diag.gc_histo`  
3. 必要时（已开危险开关）再考虑 `diag.heap_dump`  

**C. 本机开发**

1. 只启探针（HTTP 或 stdio）  
2. Agent 直连，无 Gateway  

---

## 17. 已拍板决策

| 决策 | 结论 |
|------|------|
| Registry | 仅自研 Gateway 内嵌 `InMemoryRegistry`；不对接第三方注册中心 |
| 开源范围 | 探针 + Gateway，MIT |
| 工具策略 | 一套探针实现；差别在发现/路由 |
| 多副本 | 返回 candidates，Agent 选 |
| 安全默认 | 只读、审计、无任意执行 |

---

*本文档随实现更新；若与代码冲突，以代码与 CONFIGURATION.md 为准。*
