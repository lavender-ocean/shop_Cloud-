import { defineStore } from 'pinia'
import { tokenStore } from '@shared/request'
import { getUserInfo, logout as logoutApi } from '@/api/auth'
import type { LoginResult, UserInfo } from '@shared/types'

export const useUserStore = defineStore('user', {
    state: () => ({
        userId: '',
        username: '',
        userType: 0,
        roles: [] as string[],
        permissions: [] as string[],
        accessToken: tokenStore.access
    }),
    actions: {
        setLogin(data: LoginResult) {
            tokenStore.set(data.accessToken, data.refreshToken)
            this.accessToken = data.accessToken
            this.userId = data.userId
            this.username = data.username
            this.userType = data.userType
        },
        async fetchUserInfo(): Promise<UserInfo> {
            const info = await getUserInfo()
            this.userId = info.userId
            this.username = info.username
            this.userType = info.userType
            this.roles = info.roles
            this.permissions = info.permissions
            return info
        },
        async logout() {
            try {
                // 通知服务端把 access token 拉黑（Redis 黑名单），并让 refresh 失效
                await logoutApi()
            } catch {
                // 后端登出失败也要保证本地清干净
            }
            tokenStore.clear()
            this.$reset()
            window.location.href = '/login'
        }
    }
})
