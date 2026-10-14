import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setErrorHandler, setUnauthorizedHandler } from '@shared/request'
import { useUserStore } from './stores/user'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

setUnauthorizedHandler(() => {
  useUserStore().logout()
})

setErrorHandler((message) => ElMessage.error(message))

app.mount('#app')
