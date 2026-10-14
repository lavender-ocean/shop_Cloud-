# 04 · 中间件编排

## 1. 端口（实际）

| 组件 | 端口 | 组件 | 端口 |
|---|---|---|---|
| gateway | 8080 | auth | 8081 |
| user | 8082 | product | 8083 |
| inventory | 8084 | cart | 8085 |
| order | 8086 | payment | 8087 |
| MySQL | **3307**（宿主映射，容器内 3306） | Redis | **6380**（宿主映射，容器内 6379）|
| Nacos | 8848 / 9848 | RabbitMQ | 5672 / 15672 |
| Sentinel | 8858 | Seata | 8091 / 7091 |
| XXL-JOB | 8888（内部 8080） | | |

> ⚠️ **Redis 宿主端口是 6380，不是 6379**。原先映射到 6379 时，本机 `127.0.0.1:6379` 被一个**原生 Redis**（无密码、无持久化）占用，服务走 Spring Boot 默认 `localhost:6379` 会连到那台，导致 compose 里配的 `requirepass <见 infra/.env>` 和 **AOF 持久化全部失效**。
> 现已把 compose 的 redis 宿主端口改为 **6380**，并在 Nacos `common-dev.yaml` 显式配置 `spring.data.redis: {host: 127.0.0.1, port: 6380, password: <见 infra/.env>}`。
> **排查提示**：如果怀疑服务连错了 Redis，用
> `docker exec shop-redis redis-cli -p 6379 -a <见 infra/.env> --no-auth-warning dbsize`（容器内看 6380 那台）
> 对比 `docker exec shop-redis redis-cli -h host.docker.internal -p 6379 --no-auth-warning dbsize`（宿主机原生那台）。

## 2. docker-compose（`infra/`）

统一网络 `shop-net`，容器名互访，volume 持久化，healthcheck + depends_on。

**已起组件**：mysql:8.0、redis:7（`requirepass <见 infra/.env>`）、nacos:v2.3.2（standalone + mysql）、rabbitmq:3.12-management（vhost `/shop`，用户 `shop/<见 infra/.env>`，挂 `enabled_plugins` 启用延迟插件）、sentinel-dashboard（`ochh/sentinel-dashboard:v1.8.9`，8858）、xxl-job-admin:2.4.1（映射 8888）、**seata-server:1.7.1**。

**Seata Server 关键配置**（`infra/seata/application.yml` 挂载进容器）：
```yaml
seata:
  registry:
    type: nacos
    nacos: { application: seata-server, server-addr: nacos:8848, namespace: dev, group: SEATA_GROUP, cluster: default, username: nacos, password: nacos }
  store:
    mode: file            # 单机演示；生产改 db
  security:
    secretKey: SeataSecretKey0c382ef121d778043159209298fd40bf3850a017
```
```yaml
# compose 片段
  seata:
    image: seataio/seata-server:1.7.1
    environment: { SEATA_IP: 127.0.0.1, SEATA_PORT: 8091 }   # SEATA_IP 让宿主服务能连上 TC
    ports: ["8091:8091", "7091:7091"]
    volumes: ["./seata/application.yml:/root/seata-server/resources/application.yml"]
```

**RabbitMQ 延迟插件**：`infra/rabbitmq/enabled_plugins`
```
[rabbitmq_management,rabbitmq_delayed_message_exchange].
```
> 验证：15672 控制台新建交换机时能选到 `x-delayed-message` 类型。

## 3. Nacos 配置（分层：共享 + 私有）

- **命名空间**：`dev`（自定义 ID）；**分组**：`DEFAULT_GROUP`。
- **共享配置** `common-dev.yaml`（所有服务 import），**私有配置** `shop-{service}-dev.yaml`。

**`common-dev.yaml`（实际内容）**
```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n"
management:
  endpoints:
    web: { exposure: { include: health,info } }
  endpoint:
    health: { show-details: always }
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration: { map-underscore-to-camel-case: true }
shop:
  jwt: { secret: ..., access-token-ttl: 30m, refresh-token-ttl: 7d }
spring:
  data:
    redis: { host: 127.0.0.1, port: 6379, password: <见 infra/.env>, database: 0 }
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: shop
    password: <见 infra/.env>
    virtual-host: /shop
    publisher-confirm-type: correlated
    publisher-returns: true
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: shop_tx_group
  service: { vgroup-mapping: { shop_tx_group: default } }
  registry:
    type: nacos
    nacos: { server-addr: 127.0.0.1:8848, namespace: dev, group: SEATA_GROUP, username: nacos, password: nacos }
  security: { secretKey: SeataSecretKey0c382ef121d778043159209298fd40bf3850a017 }
```
> 没有对应依赖的服务（如 product 无 amqp、gateway 无 seata）会忽略这些 key，无害。

**私有配置**：`shop-{auth|user|product|inventory|order|payment}-dev.yaml` 内含各自的 `spring.datasource`（指向自己的 schema）；cart 无私有配置（无库）。

## 4. Boot3 配置写法（每个服务）

```yaml
# src/main/resources/application.yml —— 本地只留"能连上 Nacos"的最小信息
server: { port: 8086 }
spring:
  application: { name: shop-order }
  profiles: { active: dev }
  cloud:
    nacos:
      server-addr: 127.0.0.1:8848
      username: nacos
      password: nacos
      discovery: { namespace: dev, group: DEFAULT_GROUP }
      config:    { namespace: dev, group: DEFAULT_GROUP, file-extension: yaml }
  config:
    import:
      - optional:nacos:common-dev.yaml
      - optional:nacos:shop-order-dev.yaml
```
> `spring.cloud.nacos.server-addr` **必须留在本地**（`import` 解析时要用）；`optional:` 前缀 → 配置缺失不阻断启动；端口留在本地（实例属性）。

## 5. 启动顺序

```
mysql / redis / nacos / rabbitmq
   → 初始化各 schema（含 undo_log、nacos_config、xxl_job）
   → seata / sentinel / xxl-job
   → 业务服务（auth → user → product → inventory → cart → order → payment）
   → gateway
```

## 6. 验证清单

| # | 验证 | 期望 |
|---|---|---|
| 1 | `docker compose ps` | 全 healthy |
| 2 | Nacos 服务列表(dev) | 8 个服务全部注册 |
| 3 | RabbitMQ 控制台 | `delay.exchange`（`x-delayed-message`）、`order.exchange`、`payment.exchange` + 各队列 |
| 4 | Seata 控制台(7091) | seata-server 在线；全局事务可见 `shop-create-order` 等 |
| 5 | 各服务 `/actuator/health` | UP |

## 7. 已知待办

- XXL-JOB 仅启动，未接入（当前用 `@Scheduled`）。
- Seata Server 为 **file 存储**（生产改 db）。
- Sentinel 规则未持久化到 Nacos、未接入 Feign 降级。
