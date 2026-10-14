import { createRouter, createWebHistory } from 'vue-router'
import { tokenStore } from '@shared/request'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'

const routes = [
    { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
    { path: '/404', component: () => import('@/views/NotFound.vue'), meta: { public: true } },
    {
        path: '/',
        name: 'Layout',
        component: () => import('@/layout/index.vue'),
        redirect: '/welcome',
        children: [
            { path: 'welcome', name: 'Welcome', component: () => import('@/views/Welcome.vue'), meta: { title: '首页' } }
        ]
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to) => {
    if (to.meta.public) return true
    if (!tokenStore.access) return { path: '/login' }

    const permissionStore = usePermissionStore()
    if (!permissionStore.loaded) {
        try {
            const info = await useUserStore().fetchUserInfo()
            permissionStore.buildRoutes(info.menus)
            // 动态路由已注入，重新进入目标路由
            return { ...to, replace: true }
        } catch {
            return { path: '/login' }
        }
    }
    return true
})

export default router