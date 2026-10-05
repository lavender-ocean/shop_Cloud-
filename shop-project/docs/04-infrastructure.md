# 04 · 中间件编排（D）

## D-1 端口规划

| 组件 | 端口 | 组件 | 端口 |
|---|---|---|---|
| gateway | 8080 | xxl-job-admin | 8888 ⚠️（勿用 8080） |
| auth | 8081 | mysql | 3306 |
| user | 8082 | redis | 6379 |
| product | 8083 | nacos | 8848 / 9848 |
| inventory | 8084 | rabbitmq | 5672 / 15672 |
| cart | 8085 | sentinel | 8858 |
| order | 8086 | seata | 8091 / 7091 |
| payment | 8087 | nginx | 80 |
| promotion | 8088 | (后置) es | 9200 / 9300 |
| search 后置 | 8089 | (后置) canal | 11111 |
| notification 后置 | 8090 | (后置) mongo | 27017 |

---

## D-2 docker-compose（核心批）

统一 bridge 网络 `shop-net`，容器名互访，挂 volume 持久化，配 healthcheck + depends_on。

**① RabbitMQ + 延迟插件**
```yaml
rabbitmq:
  image: rabbitmq:3.12-management
  ports: ["5672:5672", "15672:15672"]
  environment:
    RABBITMQ_DEFAULT_USER: shop
    RABBITMQ_DEFAULT_PASS: shop123
    RABBITMQ_DEFAULT_VHOST: /shop
  volumes:
    - ./rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins
  # enabled_plugins 内容：
  # [rabbitmq_management,rabbitmq_delayed_message_exchange].
```
> 3.9+ 官方镜像内置延迟插件，挂载 `enabled_plugins` 启用即可；若镜像不含，用 3 行 Dockerfile 下载 `.ez` 后 `rabbitmq-plugins enable`。验证：15672 控制台确认可选 `x-delayed-message` 类型。

**② Nacos（standalone + MySQL）**
```yaml
nacos:
  image: nacos/nacos-server:v2.3.2
  ports: ["8848:8848", "9848:9848"]
  environment:
    MODE: standalone
    SPRING_DATASOURCE_PLATFORM: mysql
    MYSQL_SERVICE_HOST: mysql
    MYSQL_SERVICE_DB_NAME: nacos_config
```
> 先建 `nacos_config` 库（官方 `mysql-schema.sql`）。

**③ Seata（注册到 Nacos）**
```yaml
seata:
  image: seataio/seata-server:1.7.1
  ports: ["8091:8091", "7091:7091"]
  environment:
    SEATA_IP: 127.0.0.1
```
> `undo_log` 要建在**每个业务 schema**里。

---

## D-3 Nacos 配置分层

- **命名空间**：本地一个 `dev`（用 namespaceId，不是名字）。
- **分组**：`DEFAULT_GROUP`。
- **分层**：共享 + 私有。

```
【共享】common-dev.yaml       日志 / Jackson / actuator / 线程池 / MyBatis-Plus
【共享】datasource-dev.yaml   MySQL / HikariCP
【共享】redis-dev.yaml        Redis / Redisson
【共享】rabbitmq-dev.yaml     MQ / confirm / ack
【共享】seata-dev.yaml        Seata / 事务分组
【私有】shop-order-dev.yaml   服务个性化
【私有】shop-gateway-dev.yaml 路由 / 白名单 / 限流
```

---

## D-4 Boot3 配置写法（关键）

`bootstrap.yml` 失效，用 `spring.config.import`：

```yaml
spring:
  application:
    name: shop-order
  profiles:
    active: dev
  cloud:
    nacos:
      server-addr: 127.0.0.1:8848
      username: nacos
      password: nacos
      discovery: { namespace: ${NACOS_NS}, group: DEFAULT_GROUP }
      config:    { namespace: ${NACOS_NS}, group: DEFAULT_GROUP, file-extension: yaml }
  config:
    import:
      - optional:nacos:common-dev.yaml
      - optional:nacos:datasource-dev.yaml
      - optional:nacos:redis-dev.yaml
      - optional:nacos:rabbitmq-dev.yaml
      - optional:nacos:seata-dev.yaml
      - optional:nacos:${spring.application.name}-${spring.profiles.active}.yaml
```

- `optional:` → 配置缺失不阻断启动。
- `spring.cloud.nacos.server-addr` 必须写在**本地** `application.yml`。

---

## D-5 Seata / Sentinel 要点

**Seata**
```yaml
seata:
  enabled: true
  application-id: ${spring.application.name}
  tx-service-group: shop_tx_group
  service:
    vgroup-mapping:
      shop_tx_group: default
  registry: { type: nacos, nacos: { server-addr: 127.0.0.1:8848, group: SEATA_GROUP } }
  config:   { type: nacos, nacos: { server-addr: 127.0.0.1:8848, group: SEATA_GROUP } }
```
- `service.vgroupMapping.shop_tx_group=default` 需在 Nacos `SEATA_GROUP` 配好。

**Sentinel**
- 服务端 `sentinel-dashboard`（8858，sentinel/sentinel）。
- 客户端 `spring.cloud.sentinel.transport.dashboard`。
- **规则持久化到 Nacos**（`sentinel-datasource-nacos`）。

---

## D-6 启动顺序与验收

**顺序**：mysql / redis / nacos / rabbitmq → 初始化 schema（含 `undo_log`、`nacos_config`）→ seata / sentinel / xxl-job → 业务服务 → gateway。

**验收：**
1. 所有容器 healthy。
2. Nacos 能见服务注册 + 配置列表。
3. RabbitMQ 能创建 `x-delayed-message` 交换机并按时投递。
4. Seata 控制台 TC 在线、分组映射正确。
5. Sentinel 能见心跳。
6. 服务能从 Nacos 读配置并启动。
