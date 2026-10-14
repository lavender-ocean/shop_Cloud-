# 01 · 工程骨架

## 1. 版本矩阵（实际）

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.2.x |
| Spring Cloud | 2023.0.x |
| Spring Cloud Alibaba | 2023.0.x |
| MyBatis-Plus | `mybatis-plus-spring-boot3-starter` 3.5.x |
| Seata | `seata-spring-boot-starter` 1.7.1 |
| jjwt | 0.12.x |

**Boot3 的两个坑（都踩过）：**
1. `bootstrap.yml` 失效 → Nacos 配置用 `spring.config.import`。
2. 命名空间 `javax.*` → `jakarta.*`。

## 2. 多模块结构（实际）

```
shop_Cloud/shop-project/
├── pom.xml                     # 父 pom
├── shop-common/                # 公共依赖聚合（packaging=pom）
│   ├── shop-common-core        # Result / 异常 / 错误码 / 常量 / 枚举 / 工具 / UserContext
│   ├── shop-common-web         # 全局异常 / traceId / Jackson / Knife4j
│   ├── shop-common-mybatis     # MP 配置（分页/乐观锁/防全表）+ BaseEntity + 自动填充
│   ├── shop-common-redis       # RedisTemplate（String key + JSON value）
│   ├── shop-common-mq          # RabbitMQ 交换机/队列声明 + JSON 转换器
│   ├── shop-common-security    # JwtUtils / AuthInterceptor / @RequirePermission
│   └── shop-common-feign       # Feign traceId 透传
├── shop-api/                   # Feign 契约 + 跨服务 DTO
├── shop-gateway/ shop-auth/ shop-user/ shop-product/
├── shop-inventory/ shop-cart/ shop-order/ shop-payment/
├── infra/                      # docker-compose + 中间件配置
├── web/                        # 前端 pnpm workspace（apps/admin, packages/shared）
└── docs/
```

## 3. 父 pom 职责

1. `<dependencyManagement>`：import 三个 BOM（spring-boot / spring-cloud / spring-cloud-alibaba）+ 三方依赖统一版本 + 内部 common 模块版本。
2. `<pluginManagement>`：`maven-compiler-plugin`（release 17）、`spring-boot-maven-plugin`。
3. **唯一例外**：父 `<dependencies>` 放 `lombok`（provided，编译期工具，避免逐模块重复写）。

> 只有**服务**模块引 `spring-boot-maven-plugin`（repackage）；common 模块是普通 jar。

## 4. common 各模块内容（实际）

**`shop-common-core`**（不依赖 Spring Web）
- `result`：`Result<T>` / `PageResult<T>` / `IErrorCode` / `ResultCode`
- `exception`：`BizException` / `BizAssert`
- `context`：`LoginUser` / `UserContext`（ThreadLocal）
- `constant`：`CommonConstants` / `SecurityConstants` / `RedisKeyConstants` / `MqConstants`
- `enums`：`OrderStatus` / `PayStatus` / `DeletedFlag`
- `util`：`JsonUtils` / `IdGenerator`

**`shop-common-web`**
- `filter.TraceIdFilter`（生成/透传 traceId → MDC → 响应头）
- `handler.GlobalExceptionHandler`（见 §6）
- `config.WebAutoConfiguration`：注册过滤器 + Jackson（**Long→String**、日期格式、忽略 null）+ OpenAPI

**`shop-common-mybatis`**
- `entity.BaseEntity` + `handler.MyMetaObjectHandler`（公共字段自动填充，操作人取 `UserContext`）
- `config.ShopMybatisAutoConfiguration`：分页 / 乐观锁 / 防全表更新删除三个拦截器

**`shop-common-redis`**
- `config.RedisAutoConfiguration`：`RedisTemplate<String,Object>`（String key + GenericJackson2Json value）

**`shop-common-mq`**
- `config.ShopRabbitAutoConfiguration`：订单/支付交换机、延时交换机（`x-delayed-message`）、队列与绑定、Jackson 消息转换器

**`shop-common-security`**
- `JwtProperties` / `JwtUtils`（HS256，含 `jti`）
- `AuthInterceptor`（读网关透传的 `X-User-*` → `UserContext`）
- `PermissionLoader`（从 Redis 读权限码）+ `PermissionInterceptor` + `@RequirePermission`
- `config.SecurityAutoConfiguration`（+ 两个嵌套 Servlet-only 子配置）

**`shop-common-feign`**
- `config.FeignTraceInterceptor`（透传 traceId）+ `config.FeignAutoConfiguration`

## 5. 自动装配（关键，也是踩坑点）

每个 common 模块在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里登记自动配置类：

```
config.WebAutoConfiguration
config.ShopMybatisAutoConfiguration
config.RedisAutoConfiguration
security.SecurityAutoConfiguration
security.SecurityAutoConfiguration$PermissionAutoConfiguration
security.SecurityAutoConfiguration$SecurityWebAutoConfiguration
config.FeignAutoConfiguration
config.ShopRabbitAutoConfiguration
```

> ⚠️ **踩坑记录**：**忘建这些 `.imports` 文件**，`@AutoConfiguration` 类根本不会被加载 → 全局异常处理、traceId 全部失效（表现为异常走了 Spring 默认 500 错误页）。排查时最容易忽略的就是这个文件。
>
> ⚠️ 网关(WebFlux) 与业务(MVC) 隔离：`spring-boot-starter-web` 在 common-security 里用 `<optional>`，Servlet 相关配置加 `@ConditionalOnClass(WebMvcConfigurer.class) + @ConditionalOnWebApplication(SERVLET)`，网关不会加载它们。

## 6. 统一返回 / 异常 / 错误码

**返回体**：`Result<T>`，`code = 0` 成功。**手动 `return Result.success(...)`**（不用 ResponseBodyAdvice 自动包装）。

**错误码分段（模块 2 位 + 序号 3 位）：**

| 段 | 模块 | 段 | 模块 |
|---|---|---|---|
| 10xxx | 通用/参数 | 15xxx | 库存 |
| 11xxx | 网关 | 16xxx | 订单 |
| 12xxx | 认证 | 17xxx | 支付 |
| 13xxx | 用户 | 18xxx | 促销 |
| 14xxx | 商品 | 19xxx | MQ/系统 |

**全局异常处理覆盖**：`BizException`、`MethodArgumentNotValidException` / `BindException`、`ConstraintViolationException`、`HandlerMethodValidationException`、`HttpRequestMethodNotSupportedException`、`HttpMessageNotReadableException`、兜底 `Exception`（全栈日志 + 返回"系统繁忙"）。

> ⚠️ **踩坑记录**：Spring 6.1 起控制器方法参数校验抛的是 **`HandlerMethodValidationException`**（不再是 `ConstraintViolationException`）。不单独处理它，会掉进兜底 10999。

## 7. 包命名现状（⚠️ 待治理）

- 业务服务：`com.shop.{service}.*`，启动类在 `com.shop`。
- common 模块：**顶层包**（`result` / `exception` / `config` / `handler` / `filter` / `security` / `util` …）。

**风险**：`config`、`handler`、`util` 等通用包名跨 jar 会造成"分裂包"，将来易与三方库或其它模块撞名。
**建议**：后续统一迁移到 `com.shop.common.core.*` / `com.shop.common.web.*` / `com.shop.common.security.*`，同时更新各 `.imports` 文件里的全限定名。

## 8. 命名与分层

- 分层：`controller / service / mapper / entity / dto / vo`
- 路径：用户端 `/api/{service}/**`，管理端 `/admin/{service}/**`，**内部接口 `/inner/**`（不加网关路由，外部不可达）**
- 对象：入参 `XxxDTO` / 出参 `XxxVO` / 实体 `Xxx`
- 表：`order_info`（`order` 是保留字）；金额一律 `BIGINT` 存**分**
