import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setErrorHandler, setUnauthorizedHandler } from '@shared/request'
import { useUserStore } from './stores/user'
import { permission } from './directive/permission'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// ⚠️ 指令必须在 mount 之前注册，否则首屏渲染拿不到 v-permission
app.directive('permission', permission)

// 401 且刷新失败 → 登出（logout 内部会整页跳转 /login）
setUnauthorizedHandler(() => {
    useUserStore().logout()
})

// 业务错误 / 网络异常统一提示
setErrorHandler((message) => ElMessage.error(message))

app.mount('#app')
