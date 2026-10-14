import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import type { Result } from './types'

const ACCESS_KEY = 'shop_access_token'
const REFRESH_KEY = 'shop_refresh_token'

export const tokenStore = {
    get access() {
        return localStorage.getItem(ACCESS_KEY) || ''
    },
    get refresh() {
        return localStorage.getItem(REFRESH_KEY) || ''
    },
    set(access: string, refresh: string) {
        localStorage.setItem(ACCESS_KEY, access)
        localStorage.setItem(REFRESH_KEY, refresh)
    },
    clear() {
        localStorage.removeItem(ACCESS_KEY)
        localStorage.removeItem(REFRESH_KEY)
    }
}

/** 未授权回调，由应用注入（shared 不依赖 router） */
let onUnauthorized: () => void = () => {}
export function setUnauthorizedHandler(fn: () => void) {
    onUnauthorized = fn
}

/** 错误提示回调，由应用注入（shared 不依赖 UI 库） */
let onError: (message: string) => void = () => {}
export function setErrorHandler(fn: (message: string) => void) {
    onError = fn
}

const instance: AxiosInstance = axios.create({ baseURL: '/', timeout: 10000 })

instance.interceptors.request.use((config) => {
    const token = tokenStore.access
    if (token) config.headers.Authorization = `Bearer ${token}`
    return config
})

let refreshing = false
let waiters: Array<(token: string) => void> = []

instance.interceptors.response.use(
    (response) => {
        const body = response.data as Result
        if (body && typeof body.code === 'number') {
            if (body.code === 0) return body.data as any
            const message = body.message || '请求失败'
            onError(message)
            return Promise.reject(new Error(message))
        }
        return body as any
    },
    async (error) => {
        const response = error?.response
        const cfg = (error?.config || {}) as any
        if (response?.status === 401 && !cfg.__isRetry) {
            cfg.__isRetry = true
            const newToken = await refreshAccessToken()
            if (newToken) {
                cfg.headers = cfg.headers || {}
                cfg.headers.Authorization = `Bearer ${newToken}`
                return instance(cfg)
            }
            tokenStore.clear()
            onError('登录已过期，请重新登录')
            onUnauthorized()
            return Promise.reject(error)
        }
        onError(response?.data?.message || error?.message || '网络异常，请稍后重试')
        return Promise.reject(error)
    }
)

/** 刷新期间其他 401 请求挂起等待，避免并发重复刷新 */
async function refreshAccessToken(): Promise<string | null> {
    if (refreshing) {
        return new Promise((resolve) => waiters.push(resolve))
    }
    const refresh = tokenStore.refresh
    if (!refresh) return null

    refreshing = true
    try {
        const { data } = await axios.post('/api/auth/refresh', { refreshToken: refresh })
        const body = data as Result<{ accessToken: string; refreshToken: string }>
        if (body.code === 0) {
            tokenStore.set(body.data.accessToken, body.data.refreshToken)
            waiters.forEach((w) => w(body.data.accessToken))
            return body.data.accessToken
        }
        waiters.forEach((w) => w(''))
        return null
    } catch {
        waiters.forEach((w) => w(''))
        return null
    } finally {
        refreshing = false
        waiters = []
    }
}

export function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
    // @ts-ignore
    return instance.request(config)
}
export default instance