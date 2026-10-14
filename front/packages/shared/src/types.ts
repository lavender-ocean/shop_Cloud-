export interface Result<T = unknown> {
    code: number
    message: string
    data: T
}

export interface LoginResult {
    accessToken: string
    refreshToken: string
    userId: string
    username: string
    userType: number
}
export interface MenuVO {
    id: string
    name: string
    path: string
    component?: string
    icon?: string
    children?: MenuVO[]
}

export interface UserInfo {
    userId: string
    username: string
    userType: number
    roles: string[]
    permissions: string[]
    menus: MenuVO[]
}

