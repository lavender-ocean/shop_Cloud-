import { request } from '@shared/request'
import type { LoginResult, UserInfo } from '@shared/types'


export function login(data: { username: string; password: string }) {
    return request<LoginResult>({ url: '/api/auth/login', method: 'post', data })
}

export function ping() {
    return request<string>({ url: '/api/user/ping', method: 'get' })
}



export function getUserInfo() {
    return request<UserInfo>({ url: '/api/auth/userinfo', method: 'get' })
}

/** 登出：让服务端把当前 access token 拉黑（Redis 黑名单），refresh 也失效 */
export function logout() {
    return request<void>({ url: '/api/auth/logout', method: 'post' })
}