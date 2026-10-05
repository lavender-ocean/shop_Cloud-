# 05 · 前端方案（E）

## E-1 工程形态与选型

**两个应用、一个 pnpm workspace monorepo：**

```
shop-web/
├── pnpm-workspace.yaml
├── packages/shared/          # request 封装 / 类型 / 工具 / 常量
└── apps/
    ├── mall/                 # 用户端（PC，Element Plus，简版；H5 后置）
    └── admin/                # 管理端（PC，Element Plus）
```

| 项 | mall | admin |
|---|---|---|
| UI 库 | Element Plus | Element Plus |
| 布局 | PC 响应式 | 经典后台（侧边栏 + 顶栏 + 标签页） |
| 权限 | 登录态 | **RBAC 动态路由 + 按钮权限** |

**技术栈**：Vue3 `<script setup>` + Vite + TypeScript + Pinia + Vue Router 4 + axios。
**规范**：ESLint + Prettier + Stylelint + Husky + lint-staged + commitlint。

---

## E-2 环境变量与代理

```bash
# apps/admin/.env.development
VITE_API_BASE_URL=/admin
```
```ts
// vite.config.ts —— 开发代理
server: {
  proxy: {
    '/admin': { target: 'http://localhost:8080', changeOrigin: true },
    '/api':   { target: 'http://localhost:8080', changeOrigin: true },
  }
}
```
> 开发用 Vite proxy，生产用 nginx，前端只认 `/api` `/admin` 前缀，**代码零改动**。

---

## E-3 axios 封装（token 刷新 + 并发排队）

```ts
const service = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL, timeout: 10000 })

service.interceptors.request.use(cfg => {
  const token = useUserStore().accessToken
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  return cfg
})

service.interceptors.response.use(
  res => {
    const { code, message, data } = res.data
    if (code === 0) return data
    ElMessage.error(message); return Promise.reject(new Error(message))
  },
  async err => {
    if (err.response?.status === 401) return handleRefreshToken(err.config)
    ElMessage.error('系统繁忙'); return Promise.reject(err)
  }
)
```

**Token 刷新 + 并发排队：**
```
access token 过期 → 第一个 401 触发刷新
                 → 刷新期间其他 401 挂入等待队列（不重复刷新）
                 → 刷新成功用新 token 重发；失败清 token 跳登录
```
用 `isRefreshing` 标志 + promise 队列实现。

**鉴权说明**：token 存 localStorage（`pinia-plugin-persistedstate`），走 `Authorization` 头；代价是 XSS 风险 → 后端做 XSS 防护 + accessToken 短时效。

---

## E-4 Pinia Store

```
mall:  userStore / cartStore / addressStore
admin: userStore（token/userInfo/roles/permissions）
       permissionStore（动态路由）
       appStore（侧边栏/主题）
       tagsViewStore（页签缓存）
```
> 原则：服务端状态不长期驻留前端，store 只放跨页面共享 + 需持久化的状态。

---

## E-5 动态权限（admin 核心）

```
进入路由 → 有 token?
   ├─ 无 → 登录页
   └─ 有 → 是否已拉取用户信息?
        ├─ 否 → /admin/auth/userinfo 拿 roles/permissions + 菜单树
        │        → 生成动态路由 → router.addRoute()
        │        → 存 permissionStore → next({...to, replace:true})
        └─ 是 → 放行
```

**两个细节：**
1. F5 后 `addRoute` 丢失 → 守卫里检查并重建。
2. 404 兜底路由**最后** `addRoute`，否则刷新命中 404。

菜单树由 `sys_permission` 驱动，前后端权限模型对齐。

---

## E-6 按钮级权限

```html
<el-button v-permission="'order:cancel'">取消订单</el-button>
```
> 前端隐藏按钮只是体验，**真正鉴权在后端**（网关 + 服务层）。

---

## E-7 nginx 反代 + 灰度入口

```nginx
server {
  listen 80;
  location / { root /usr/share/nginx/html/mall; try_files $uri $uri/ /index.html; }
  location /admin-ui/ { root /usr/share/nginx/html/admin; try_files $uri $uri/ /admin-ui/index.html; }
  location /api/   { proxy_pass http://shop-gateway:8080; }
  location /admin/ { proxy_pass http://shop-gateway:8080; }
  gzip on;
}
```
- `try_files` 解决 history 刷新 404。
- 生产同源，跨域消失。
- **灰度入口**：按 cookie/header 分流到灰度网关（亮点）。

---

## E-8 接口契约

| 项 | 约定 |
|---|---|
| 用户端路径 | `/api/{service}/...` |
| 管理端路径 | `/admin/{service}/...` |
| 返回结构 | `{ code, message, data }`，**code=0 成功** |
| 分页入参 | `{ pageNum, pageSize }` |
| 分页出参 | `{ list, total, pageNum, pageSize }` |
| 大整数 | 后端 Long→String，前端按 string 处理 |
| 鉴权 | 登录返 `accessToken` + `refreshToken` |
| 权限码 | 与 `sys_permission.perm_code` 一致 |

---

## E-9 页面清单

- **mall**：首页/类目 / 商品列表 / 商品详情 / 购物车 / 结算 / 支付 / 订单列表·详情 / 地址管理 / 登录注册 / 个人中心
- **admin**：登录 / 看板 / 商品(SPU·SKU) / 类目·品牌 / 库存 / 订单 / 用户 / 营销 / 系统(用户·角色·权限)

---

## E-10 验收

1. admin 登录后按角色渲染动态菜单，刷新不掉路由。
2. 越权访问前端被拦、后端也拒绝。
3. access token 过期自动刷新重发，刷新失败跳登录。
4. nginx 部署后三端同源可访问。
