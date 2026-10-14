import { request } from '@shared/request'

export interface OrderItem {
    id: string
    skuId: string
    skuName: string
    skuImage?: string
    specJson?: string
    price: string
    quantity: number
    totalAmount: string
}

export interface OrderInfo {
    id: string
    orderNo: string
    totalAmount: string
    discountAmount: string
    freightAmount: string
    payAmount: string
    status: number
    payTime?: string
    receiverName?: string
    receiverPhone?: string
    receiverProvince?: string
    receiverCity?: string
    receiverDistrict?: string
    receiverDetail?: string
    remark?: string
    expireTime?: string
    createTime?: string
}

export interface OrderDetail {
    order: OrderInfo
    items: OrderItem[]
}

export interface Page<T> {
    list: T[]
    total: string
    pageNum: string
    pageSize: string
}

export function getOrderPage(params: { pageNum: number; pageSize: number; status?: number }) {
    return request<Page<OrderInfo>>({ url: '/admin/order/page', method: 'get', params })
}

export function getOrderDetail(orderNo: string) {
    return request<OrderDetail>({ url: `/admin/order/${orderNo}`, method: 'get' })
}

export function shipOrder(orderNo: string) {
    return request<void>({ url: `/admin/order/${orderNo}/ship`, method: 'put' })
}

/** 金额：分 → 元 */
export function yuan(fen: string | number | undefined): string {
    return (Number(fen || 0) / 100).toFixed(2)
}

export const ORDER_STATUS_TEXT: Record<number, string> = {
    0: '待支付',
    1: '待发货',
    2: '配送中',
    3: '已完成',
    4: '已取消',
    5: '已关闭',
    6: '退款中',
    7: '已退款'
}

export const ORDER_STATUS_TYPE: Record<number, 'warning' | 'success' | 'info' | 'danger' | 'primary'> = {
    0: 'warning',
    1: 'success',
    2: 'primary',
    3: 'info',
    4: 'info',
    5: 'info',
    6: 'danger',
    7: 'danger'
}