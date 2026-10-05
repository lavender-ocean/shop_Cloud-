# 01 · 工程骨架（A）

## A-1 版本矩阵

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | 大厂主流 |
| Spring Boot | 3.2.x | WebFlux 与 MVC 基线 |
| Spring Cloud | 2023.0.x | 对应 Boot 3.2 |
| Spring Cloud Alibaba | 2023.0.x | Nacos / Sentinel / Seata |
| MyBatis-Plus | 3.5.5+ | **Boot3 用 `mybatis-plus-spring-boot3-starter`** |
| Knife4j | 4.x (openapi3) | 接口文档 |

**Boot3 两个坑：**
1. `bootstrap.yml` 默认失效，Nacos 配置改用 `spring.config.import`。
2. 命名空间从 `javax.*` 迁到 `jakarta.*`。

> 具体版本以官方版本对应关系为准。

---

## A-2 仓库多模块结构

```
shop_Cloud/
├── pom.xml                    # 父 pom（packaging=pom）
├── shop-common/               # 公共依赖聚合（packaging=pom）
│   ├── shop-common-core       # Result / 异常 / 错误码 / 常量 / 枚举 / 工具 / UserContext
│   ├── shop-common-web        # 全局异常 / traceId / Jackson / Knife4j / 校验
│   ├── shop-common-mybatis    # MP 配置 / BaseEntity / 自动填充 / 分页 / 乐观锁
│   ├── shop-common-redis      # RedisTemplate / Redisson / 缓存 key 规范
│   ├── shop-common-mq         # MQ 配置 / 消息基类 / 可靠投递 / 消费幂等
│   ├── shop-common-security   # JwtUtils / 鉴权注解 / 上下文拦截器
│   └── shop-common-feign      # Feign 拦截器 / 错误解码 / 降级基类
├── shop-api/                  # 各服务 Feign 接口 + 共享 DTO（跨服务契约）
├── shop-gateway/
├── shop-auth/
├── shop-user/
├── shop-product/
├── shop-inventory/
├── shop-cart/
├── shop-order/
├── shop-payment/
└── shop-promotion/
```

**关键取舍：**
- **common 拆 7 个**：服务按需依赖，避免被迫引入无关中间件；代价是多模块维护成本。
- **单 `shop-api` 模块**：集中 Feign 契约 + DTO，避免服务间循环依赖；替代方案是每个服务自带 `xxx-api` 子模块（更企业级，模块数翻倍）。

---

## A-3 父 pom 职责（只做两件事）

1. `<dependencyManagement>`：import 四个 BOM（`spring-boot-dependencies`、`spring-cloud-dependencies`、`spring-cloud-alibaba-dependencies`、`mybatis-plus-bom`）+ 三方依赖统一版本。
2. `<pluginManagement>`：`maven-compiler-plugin`（release 17）、`spring-boot-maven-plugin`、`flatten-maven-plugin`。

> 原则：**父 pom 不放 `<dependencies>`**（避免强制传染）。

---

## A-4 common 各模块内容清单

**`shop-common-core`**（最底层，不依赖 Spring Web）
- `Result<T>` / `PageResult<T>`
- `IErrorCode` + `ResultCode` 枚举 + `BizException` + `BizAssert`
- `UserContext`（ThreadLocal：userId / username / roles）
- 常量：`CommonConstants` / `RedisKeyConstants` / `MqConstants` / `SecurityConstants`
- 枚举：`OrderStatus` / `PayStatus` / `DeletedFlag`
- 工具：`JsonUtils` / `IdGenerator`（雪花）

**`shop-common-web`**
- `GlobalExceptionHandler`（`@RestControllerAdvice`）
- `TraceIdFilter`（`OncePerRequestFilter`，写 MDC）
- Jackson：**Long → String**、日期格式、空值策略
- `@Validated` 分组校验、Knife4j 配置

**`shop-common-mybatis`**
- MP 拦截器：分页、乐观锁、防全表更新删除
- `BaseEntity`：`id / create_time / update_time / create_by / update_by / deleted / version`
- `MyMetaObjectHandler`：公共字段自动填充

**`shop-common-redis`**
- `RedisTemplate`（key=String，value=JSON）+ `RedissonClient`
- 分布式锁工具 + 缓存 key 生成规范

**`shop-common-mq`**
- RabbitMQ 配置（Jackson 转换器、发送确认、手动 ack）
- `BaseMessage`（`msgId / bizType / timestamp / traceId`）+ 消费幂等抽象
- 交换机 / 队列 / 路由键常量

**`shop-common-security`**
- `JwtUtils`、`@RequirePermission` / `@RequireRole`
- `SecurityInterceptor`：解析请求头 → `UserContext`

**`shop-common-feign`**
- `RequestInterceptor`：透传 traceId + 用户身份
- `ErrorDecoder` → `BizException`；降级工厂基类

**依赖方向（单向）：** `core` ← 其他所有；其余六者互不依赖。

---

## A-5 自动装配（starter 化）

每个 common 模块提供：

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

配置类用 `@AutoConfiguration` + `@ConditionalOnMissingBean`。业务服务引依赖即生效，无需写配置。

---

## A-6 统一返回 / 异常 / 错误码

```java
public class Result<T> {
    private int code;        // 0 = 成功
    private String message;
    private T data;
}
```

**错误码分段（模块 2 位 + 序号 3 位）：**

| 段 | 模块 | 段 | 模块 |
|---|---|---|---|
| 10xxx | 通用 / 参数 | 15xxx | 库存 |
| 11xxx | 网关 | 16xxx | 订单 |
| 12xxx | 认证 | 17xxx | 支付 |
| 13xxx | 用户 | 18xxx | 促销 |
| 14xxx | 商品 | 19xxx | MQ / 系统 |

**全局异常处理覆盖：** `BizException`、参数校验异常（`MethodArgumentNotValidException` / `ConstraintViolationException`）、`FeignException`、兜底 `Exception`（日志打全栈，返回前端只给"系统繁忙"）。

**返回方式：** 手动 `return Result.success(...)`，不用 `ResponseBodyAdvice` 自动包装（显式、避免返回 `String` / 文件流时踩坑）。

---

## A-7 命名与分层规范

- 包名 `com.shop.{service}`，分层 `controller / service / manager / mapper / entity / dto / vo / convert`
- 路径：用户端 `/api/{service}/**`，管理端 `/admin/{service}/**`
- 对象：入参 `XxxDTO` / 出参 `XxxVO` / 实体 `Xxx`
- Git：`main / dev / feature-*` + Conventional Commits

---

## A-8 P0 验收

1. `mvn clean install` 全绿，父 pom 无版本冲突。
2. 空服务 `shop-user` 的 `GET /api/user/ping` 返回标准 `Result`。
3. `BizException` 与参数校验错误返回规范错误码。
4. 一次请求的日志中 traceId 贯穿。
5. Redis / MP 不写配置即可用（自动装配生效）。
