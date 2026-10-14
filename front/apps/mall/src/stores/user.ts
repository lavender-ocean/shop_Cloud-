import { defineStore } from 'pinia'
import { tokenStore } from '@shared/request'
import { logout as logoutApi, type LoginResult } from '@/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    userId: '',
    username: '',
    accessToken: tokenStore.access
  }),
  getters: {
    isLogin: (state) => !!state.accessToken
  },
  actions: {
    setLogin(data: LoginResult) {
      tokenStore.set(data.accessToken, data.refreshToken)
      this.accessToken = data.accessToken
      this.userId = data.userId
      this.username = data.username
    },
    async logout() {
      try {
        await logoutApi()   // 服务端拉黑 token
      } catch {
        // 忽略，保证本地清干净
      }
      tokenStore.clear()
      this.accessToken = ''
      this.userId = ''
      this.username = ''
      window.location.href = '/login'
    }
  }
})
