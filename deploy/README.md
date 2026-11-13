# Deploy helpers

[English](README_EN.md)

本目录是发版 / 本机部署用的脚本与模板，与 [GitHub Release](https://github.com/chenxk/java-diag/releases/latest) 解压结构一致。

- `java-diag-probe/` / `java-diag-gateway/` — `start.sh`（及 Windows `start.cmd` / `start.ps1`）+ properties 模板  
- `Dockerfile.probe` / `Dockerfile.gateway` — 容器镜像  
- `k8s/` — Kubernetes sidecar / DaemonSet 草稿（见 [`k8s/README.md`](k8s/README.md)）

**完整上手步骤（单机直连探针 → Gateway+Probe、Agent 配 `/mcp`）见仓库根 [README.md](../README.md) Quick Start**；此处只列部署要点，不重复整篇。

## 构建产物

**Fat jars 不入库。** 构建后拷贝：

```bash
mvn -q clean package
cp java-diag-probe/target/java-diag-probe.jar deploy/java-diag-probe/
cp java-diag-gateway/target/java-diag-gateway.jar deploy/java-diag-gateway/
```

## 启动与端口

```bash
# 探针（默认 MCP :8765）
cd deploy/java-diag-probe
chmod +x start.sh && ./start.sh start   # stop | restart | status | foreground

# 网关（默认 :8787；集群时先启 Gateway，再启 Probe）
cd deploy/java-diag-gateway
chmod +x start.sh && ./start.sh start
```

- Agent 推荐 HTTP MCP：`http://<host>:8765/mcp`（直连探针）或 `http://<host>:8787/mcp`（只连 Gateway）
- Admin：`http://<gateway-host>:8787/admin/`（`admin.user` / `admin.password`）

## 配置要点

改 properties 里全部 `CHANGE_ME_*`（或 K8s Secrets）。关键键：

| 角色 | 键 | 说明 |
|------|-----|------|
| Probe / Gateway | `auth.token` | 各自 MCP Bearer（可不同） |
| 两端 | `register.token` | **必须一致**；探针调 Gateway `/registry/*` |
| Gateway | `admin.user` / `admin.password` | 浏览器 Admin |
| Probe（挂 Gateway 时） | `register.url` / `advertise.url` | Gateway 根 URL（勿带 `/mcp`）；回调探针可达地址 |

单机只跑探针时：填 `auth.token` + `register.token` 即可，不必填 `register.url`。全集见 [docs/CONFIGURATION.md](../docs/CONFIGURATION.md)；共享环境见 [SECURITY.md](../SECURITY.md)。
