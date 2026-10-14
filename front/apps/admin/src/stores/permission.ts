import { defineStore } from 'pinia'
import router from '@/router'
import type { MenuVO } from '@shared/types'
import type { RouteRecordRaw } from 'vue-router'
// ✅ 导入Layout布局组件
import Layout from '@/layout/index.vue'

export const usePermissionStore = defineStore('permission', {
    state: () => ({
        loaded: false,
        menus: [] as MenuVO[]
    }),
    actions: {
        buildRoutes(menus: MenuVO[]) {
            this.menus = menus
            const views = import.meta.glob('../views/**/*.vue')

            const buildRouteTree = (list: MenuVO[]): RouteRecordRaw[] => {
                const normalizePath = (p: string) => p.startsWith('/') ? p.slice(1) : p

                return list.map((m) => {
                    const rawRoute: Record<string, any> = {
                        path: normalizePath(m.path),
                        name: m.component?.replace(/\//g, '-'),
                        meta: {
                            title: m.name,
                            icon: m.icon
                        }
                    }

                    // ========== 重点修复：Layout特殊判断 ==========
                    if (m.component === 'Layout') {
                        // 父菜单直接赋值Layout组件，不去views找文件
                        rawRoute.component = Layout
                    } else if (m.component) {
                        // 子页面才走views下的vue文件加载
                        const key = `../views/${m.component}.vue`
                        if (Object.prototype.hasOwnProperty.call(views, key)) {
                            rawRoute.component = views[key]
                        } else {
                            console.warn(`【动态路由】找不到组件: ${key}`)
                        }
                    }

                    if (m.children?.length) {
                        rawRoute.children = buildRouteTree(m.children)
                    }
                    return rawRoute as unknown as RouteRecordRaw
                })
            }

            const dynamicRoutes = buildRouteTree(menus)
            dynamicRoutes.forEach(route => {
                router.addRoute('Layout', route)
            })

            // 404兜底，所有动态路由添加完成后注册
            router.addRoute({
                path: '/:pathMatch(.*)*',
                name: 'NotFound',
                redirect: '/404'
            })
            this.loaded = true
        },
        reset() {
            this.loaded = false
            this.menus = []
            router.getRoutes().forEach(route => {
                const routeName = route.name as string
                if (routeName && !['Layout', 'NotFound', 'Welcome'].includes(routeName)) {
                    router.removeRoute(routeName)
                }
            })
        }
    }
})
