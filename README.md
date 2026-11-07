# java-diag

只读、可审计的 Java 线上排查 MCP。

- **探针** `java-diag-mcp-server`：部署在业务机，HTTP MCP（也可 `--stdio`）
- **网关** `java-diag-gateway`：集群入口 + 内置心跳注册；Agent 可只连 Gateway，也仍可直连探针

## 构建

```bash
mvn -q clean package
```

产物：

- `java-diag-mcp-server/target/java-diag-mcp-server.jar`
- `java-diag-gateway/target/java-diag-gateway.jar`

## 配置

全部 CLI / 环境变量（传输、host/port、token、日志白名单、超时、输出上限、危险工具、审计路径、注册与 TTL 等）见：

**[docs/CONFIGURATION.md](docs/CONFIGURATION.md)**

```bash
java -jar java-diag-mcp-server.jar --help
java -jar java-diag-gateway.jar --help
```

加载顺序：环境变量 → CLI 覆盖。无独立配置文件。

## 单机探针

```bash
java -jar java-diag-mcp-server/target/java-diag-mcp-server.jar \
  --host 0.0.0.0 --port 8765 --token probe-secret
```

Agent 直连：

```json
{
  "mcpServers": {
    "java-diag": {
      "url": "http://10.0.1.11:8765/sse",
      "headers": { "Authorization": "Bearer probe-secret" }
    }
  }
}
```

## 集群（心跳注册）

### 1) 启动 Gateway

```bash
java -jar java-diag-gateway/target/java-diag-gateway.jar \
  --host 0.0.0.0 --port 8787 \
  --token agent-secret
```

Registry（`/registry/*`）无共享密钥：信任内网；Agent→Gateway 仍用 `--token`。

### 2) 启动探针并注册

`--service-name` / `JAVA_DIAG_SERVICE_NAME` 与 `--instance-id` / `JAVA_DIAG_INSTANCE_ID` 均可选；未设置时均默认为本机主网卡 IP（非 hostname；同一解析）。

```bash
java -jar java-diag-mcp-server/target/java-diag-mcp-server.jar \
  --host 0.0.0.0 --port 8765 --token probe-secret \
  --service-name order-service --instance-id order-1 --tag env=prod \
  --register-url http://gateway-host:8787 \
  --advertise-url http://10.0.1.11:8765
```

### 3) Agent 只连 Gateway

```json
{
  "mcpServers": {
    "java-diag-cluster": {
      "url": "http://gateway-host:8787/sse",
      "headers": { "Authorization": "Bearer agent-secret" }
    }
  }
}
```

剧本：`diag.list_instances` → 多命中时看 `candidates` → 带 `target.instanceId` 再调 `diag.thread_dump` 等。  
**多副本不会静默选机。**

## 锁定决策（集群）

| 项 | 结论 |
|----|------|
| 发现 | 心跳注册（Built-in Registry） |
| Gateway | 独立 jar |
| 直连探针 | 允许 |
| 多副本 | 返回 candidates，Agent 选 |
| Registry 鉴权 | 信任内网，无 register-token；Agent→Gateway 保留 `--token` |

详见项目 Agent Store：`docs/CLUSTER_DESIGN.md`。

## 工具（探针）

Meta / Logs / JVM：`diag.ping` … `diag.gc_histo`（共 12 个）。Gateway 另提供 `diag.list_instances` 并代理上述工具。

## 测试

```bash
mvn -q test
```

## License

MIT
