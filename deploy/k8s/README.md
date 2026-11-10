# Java Diag — Kubernetes 部署说明（草稿）

本目录给出 **Gateway + 业务 Sidecar 探针** 的最小可跑清单。  
java-diag **不用为 K8s 改诊断逻辑**；关键是把探针部署到「能看见业务 JVM、能读到日志」的位置。

> 镜像地址、业务启动命令、日志路径均为占位，上线前请替换。

## 目录

| 文件 | 说明 |
|------|------|
| `00-namespace-secrets.yaml` | 命名空间 `java-diag` + Token / Admin 账号 |
| `10-gateway.yaml` | 集群入口 Gateway（Deployment + Service） |
| `20-app-with-sidecar.yaml` | **推荐**：业务 Pod + 探针 Sidecar |
| `30-daemonset-alt.yaml` | 备选：节点级 DaemonSet（权限更大，一般不优先） |

## 架构（简述）

```
Agent / IDE
    │  MCP SSE + Bearer
    ▼
java-diag-gateway  (:8787)
    │  按心跳注册表选探针（多副本返回 candidates）
    ▼
业务 Pod
  ├─ app（业务 JVM）
  └─ java-diag sidecar (:8765)  ← shareProcessNamespace + 共享日志卷
```

## 前置条件

1. 已构建并推送镜像：
   - `your-registry/java-diag-gateway:<tag>`
   - `your-registry/java-diag-mcp-server:<tag>`（建议基于带 `jps`/`jcmd` 的 JDK 镜像打包）
2. 集群内 Gateway 能访问各 Pod 的 **advertise 地址**（默认 `http://<PodIP>:8765`）
3. 业务日志写到 Sidecar 也能挂上的目录（示例用 `/var/log/app`）

## 快速部署

```bash
# 1) 把 00-namespace-secrets.yaml 里全部 CHANGE_ME_* 改成强随机值
# 2) 把 YAML 中的 your-registry/... 换成真实镜像
# 3) 按需改 20 里的业务镜像与日志路径

kubectl apply -f 00-namespace-secrets.yaml
kubectl apply -f 10-gateway.yaml
kubectl apply -f 20-app-with-sidecar.yaml
```

检查：

```bash
kubectl -n java-diag get pods,svc
kubectl -n java-diag logs deploy/java-diag-gateway --tail=50
kubectl -n java-diag logs deploy/demo-order-service -c java-diag --tail=50
```

管理控制台（需把 Gateway 端口暴露到本机或经 Ingress）：

- URL：`http://<gateway-host>:8787/admin/`
- 账号见 Secret：`admin-user` / `admin-password`（勿使用仓库占位值）

## Sidecar 必配项（`20-app-with-sidecar.yaml`）

| 项 | 作用 |
|----|------|
| `shareProcessNamespace: true` | Sidecar 与业务共享 PID，才能 `jps` / `jcmd` |
| 共享日志 Volume + `JAVA_DIAG_ALLOWED_LOG_DIRS` | 才能 `diag.log_paths` / `grep_log` / `cluster_errors` |
| `JAVA_DIAG_REGISTER_URL` | 指向 Gateway，例如 `http://java-diag-gateway.java-diag.svc.cluster.local:8787` |
| `JAVA_DIAG_ADVERTISE_URL` | Gateway 回调地址，示例为 `http://$(POD_IP):8765` |
| `JAVA_DIAG_SERVICE_NAME` / `JAVA_DIAG_TAGS` / `JAVA_DIAG_APP_NAME_MAP` | 便于按业务名、标签发现实例 |
| `JAVA_DIAG_AUTH_TOKEN` | 与 Gateway 代理探针时使用的探针 Token 一致 |

若入口脚本不展开 `$(POD_IP)`，可改用启动命令自行拼接 `advertise.url`。

## Agent 怎么连

只连 Gateway（不要直连每个 sidecar，除非排障）：

```json
{
  "mcpServers": {
    "java-diag-cluster": {
      "url": "http://<gateway-host>:8787/sse",
      "headers": { "Authorization": "Bearer YOUR_AGENT_TOKEN" }
    }
  }
}
```

典型诊断顺序：

1. `diag.list_instances`（可按 `app` / `serviceName` 过滤）或 `diag.resolve_service`
2. 多副本时看 `candidates`，选定后再带 `target`
3. 带 `target` + `pid` 调用 `diag.thread_dump`、`diag.grep_log`、`diag.cluster_errors` 等

**多副本不会静默选机。**

## DaemonSet 备选（`30-daemonset-alt.yaml`）

适合：暂时不想改每个业务 Deployment。

代价：

- 需要 `hostPID`（权限面更大）
- 容器 PID 与宿主 PID 映射更绕
- 日志依赖 `hostPath` / 约定目录，不如 Sidecar 清晰

多数场景仍推荐 Sidecar。

## 常见问题

**探针列表有实例，但 thread_dump / jcmd 失败**  
检查是否开启 `shareProcessNamespace`，以及探针镜像内 JDK 工具是否可用、与业务 JDK 是否兼容。

**能列进程，但搜不到日志**  
确认业务日志目录已挂到 sidecar，且路径在 `JAVA_DIAG_ALLOWED_LOG_DIRS` 白名单内。

**Gateway 上看不到探针 / 心跳过期**  
核对 `JAVA_DIAG_REGISTER_URL`、`JAVA_DIAG_ADVERTISE_URL`（集群内可达）、网络策略，以及 Gateway 的 heartbeat TTL。

**`name` / `mainClass` 显示成某个依赖 jar**  
`-classpath ... MainClass` 启动时，请使用已包含 classpath 解析修复的探针版本；也可用 `JAVA_DIAG_APP_NAME_MAP` 映射成业务名。

## 相关文档

- 配置项全集：[`docs/CONFIGURATION.md`](../../docs/CONFIGURATION.md)
- 集群能力与工具：仓库根目录 [`README.md`](../../README.md)
- 架构说明：[`docs/TECH_ARCHITECTURE.md`](../../docs/TECH_ARCHITECTURE.md)
