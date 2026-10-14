import { request } from '@shared/request'

export interface CategoryNode {
    id: string
    parentId: string
    name: string
    level: number
    sort: number
    icon?: string
    status: number
    children?: CategoryNode[]
}

export interface SpuRow {
    id: string
    spuCode: string
    name: string
    categoryId: string
    brandId?: string
    mainImage?: string
    priceMin: string
    priceMax: string
    unit?: string
    isColdChain: number
    status: number
}

export interface SkuRow {
    id?: string
    name: string
    specJson?: string
    image?: string
    price?: number
    status?: number
}

export interface SpuDetail extends SpuRow {
    images: string[]
    detail?: string
    skus: SkuRow[]
}

export interface Brand {
    id: string
    name: string
}

export interface Page<T> {
    list: T[]
    total: string
    pageNum: string
    pageSize: string
}

export function getCategoryTree() {
    return request<CategoryNode[]>({ url: '/admin/product/category/tree', method: 'get' })
}

export function getBrandList() {
    return request<Brand[]>({ url: '/admin/product/brand/list', method: 'get' })
}

export function getSpuPage(params: {
    pageNum: number
    pageSize: number
    name?: string
    categoryId?: string
    status?: number
}) {
    return request<Page<SpuRow>>({ url: '/admin/product/spu/page', method: 'get', params })
}

export function getSpuDetail(id: string) {
    return request<SpuDetail>({ url: `/admin/product/spu/${id}`, method: 'get' })
}

export function createSpu(data: unknown) {
    return request<string>({ url: '/admin/product/spu', method: 'post', data })
}

export function updateSpu(data: unknown) {
    return request<void>({ url: '/admin/product/spu', method: 'put', data })
}

export function updateSpuStatus(id: string, status: number) {
    return request<void>({ url: `/admin/product/spu/${id}/status/${status}`, method: 'put' })
}

export function deleteSpu(id: string) {
    return request<void>({ url: `/admin/product/spu/${id}`, method: 'delete' })
}