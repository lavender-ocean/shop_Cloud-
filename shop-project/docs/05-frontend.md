# 05 · 前端方案

> 现状：**仅实现了管理端（`apps/admin`）**；用户端（`apps/mall`）尚未创建。

## 1. 工程形态（实际）

```
shop-project/web/
├── pnpm-workspace.yaml
├── package.json
├── packages/shared/            # request 封装 + 类型（双端共享）
│   └── src/{index.ts, request.ts, types.ts}
└── apps/admin/                 # 管理端（Vue3 + TS + Element Plus）
    └── src/
        ├── main.ts  App.vue  vite-env.d.ts
        ├── api/{auth.ts, product.ts}
        ├── stores/{user.ts, permission.ts}
        ├── router/index.ts
        ├── layout/index.vue      # 布局（侧边栏 + 顶栏 + router-view）
        ├── components/MenuTree.vue   # 递归菜单
        ├── directive/permission.ts   # v-permission
        ├── utils/icons.ts
        └── views/{Login, Welcome, NotFound, product/manage/index, ...}
```

- **技术栈**：Vue3 `<script setup>` + Vite + TypeScript + Pinia + Vue Router 4 + Element Plus。
- **`@shared` 别名**：Vite `resolve.alias` 直接指向 `packages/shared/src`（绕开工作区依赖解析）。
- 开发代理：`/api` 与 `/admin` → `http://localhost:8080`（网关）。

## 2. request 封装（`packages/shared/src/request.ts`）

- axios 实例，`baseURL = '/'`。
- **请求拦截**：`Authorization: Bearer {token}`（token 存 localStorage）。
- **响应拦截**：解包 `Result`（`code===0` 返回 `data`，否则 reject）。
- **401 刷新 + 并发排队**：`isRefreshing` 标志 + `waiters` 队列；刷新接口 `/api/auth/refresh`；失败则清 token 并回调 `setUnauthorizedHandler`。
- 应用侧注入未授权处理（`main.ts` 里 `setUnauthorizedHandler` → 登出 + 跳登录），**shared 不依赖 router**。

## 3. 鉴权与动态菜单（核心）

**路由分两类**：静态（`/login`、`/404`、`Layout` + `welcome`）+ **动态（来自后端 `/api/auth/userinfo` 的菜单树）**。

**守卫流程**（`router/index.ts`）：
```
进入路由 → meta.public? → 放行
        → 无 token → /login
        → permissionStore.loaded? → 放行
        → 拉 /userinfo → buildRoutes(menus) → 重新进入 (replace)
```

**`permissionStore.buildRoutes`**：
- 用 `import.meta.glob('../views/**/*.vue')` 映射 `component` 字段（如 `product/manage/index`）到真实组件；
- `router.addRoute('Layout', route)`；
- **最后**加 404 兜底路由（必须在动态路由之后，否则刷新时误命中 404）。

**两个踩坑点**：
1. **F5 后动态路由丢失** → 守卫用 `loaded` 标志重建。
2. **404 兜底必须最后加**。

**按钮级权限**：`v-permission="'product:spu:add'"` 指令，无权限则移除元素。前端隐藏**只是体验**，真正防线在后端（网关 + 服务端 `@RequirePermission`）。

## 4. Pinia Store

```
userStore       token / userId / username / userType / roles / permissions
                setLogin / fetchUserInfo / logout(整页刷新以清动态路由)
permissionStore loaded / menus / buildRoutes / reset
```

## 5. 已实现页面

| 页面 | 路径 | 说明 |
|---|---|---|
| 登录 | `/login` | 调 `/api/auth/login`，存 token |
| 布局 | `Layout` | 侧边栏（动态菜单）+ 顶栏（用户/角色/退出） |
| 首页 | `/welcome` | 欢迎页 |
| 商品管理 | `/product/spu` | 类目树筛选 + SPU 分页 + 新增/编辑(含 SKU 子表) + 上下架 + 删除 |
| 用户管理/角色管理/订单列表 | 菜单占位 | 仅占位页 |

> 菜单 via `sys_permission.component` → 前端组件路径。新增页面只需新建组件 + 更新 `component` 字段。

## 6. 未完成

- **用户端 `apps/mall`**（购物车、下单、订单列表）。
- **nginx 反向代理 + 生产构建部署**（开发用 Vite proxy）。
- 动态路由的 tagsView 缓存、按钮权限的细粒度刷新。
