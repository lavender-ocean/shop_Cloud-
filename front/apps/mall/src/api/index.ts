import { request } from '@shared/request'

/* ============ 通用分页 ============ */
export interface Page<T> {
  list: T[]
  total: string
  pageNum: string
  pageSize: string
}

/* ============ 认证 ============ */
export interface LoginResult {
  accessToken: string
  refreshToken: string
  userId: string
  username: string
  userType: number
}

export function login(data: { username: string; password: string }) {
  return request<LoginResult>({ url: '/api/auth/login', method: 'post', data })
}

/** 登出：让服务端把 access token 拉黑 */
export function logout() {
  return request<void>({ url: '/api/auth/logout', method: 'post' })
}

/* ============ 商品 ============ */
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

export interface Spu {
  id: string
  spuCode: string
  name: string
  categoryId: string
  mainImage?: string
  priceMin: string
  priceMax: string
  unit?: string
  isColdChain: number
  status: number
}

export interface Sku {
  id: string
  spuId: string
  spuName?: string
  name: string
  specJson?: string
  image?: string
  price: string
  status: number
}

export interface SpuDetail {
  id: string
  spuCode: string
  name: string
  categoryId: string
  mainImage?: string
  images: string[]
  detail?: string
  priceMin: string
  priceMax: string
  unit?: string
  isColdChain: number
  status: number
  skus: Sku[]
}

export function getCategoryTree() {
  return request<CategoryNode[]>({ url: '/api/product/category/tree', method: 'get' })
}

export function getSpuPage(params: { pageNum: number; pageSize: number; name?: string; categoryId?: string }) {
  return request<Page<Spu>>({ url: '/api/product/spu/page', method: 'get', params })
}

export function getSpuDetail(id: string) {
  return request<SpuDetail>({ url: `/api/product/spu/${id}`, method: 'get' })
}

/* ============ 购物车 ============ */
export interface CartItem {
  skuId: string
  spuId?: string
  spuName?: string
  skuName: string
  image?: string
  specJson?: string
  price: string
  quantity: number
  selected: boolean
  addTime?: string
  /** 商品是否有效（未下架、未删除）；失效商品不可勾选与结算 */
  valid: boolean
  invalidReason?: string
}

export interface CartVO {
  items: CartItem[]
  totalQuantity: number
  selectedQuantity: number
  selectedAmount: number
}

export function addToCart(data: { skuId: string; quantity: number }) {
  return request<void>({ url: '/api/cart/add', method: 'post', data })
}

export function getCart() {
  return request<CartVO>({ url: '/api/cart/list', method: 'get' })
}

export function updateCartQuantity(data: { skuId: string; quantity: number }) {
  return request<void>({ url: '/api/cart/quantity', method: 'put', data })
}

export function updateCartSelected(data: { skuId: string; selected: boolean }) {
  return request<void>({ url: '/api/cart/selected', method: 'put', data })
}

export function removeCartItem(skuId: string) {
  return request<void>({ url: `/api/cart/${skuId}`, method: 'delete' })
}

/* ============ 地址 ============ */
export interface Address {
  id: string
  receiverName: string
  receiverPhone: string
  province?: string
  city?: string
  district?: string
  detailAddress: string
  tag?: string
  isDefault: number
}

export function getAddressList() {
  return request<Address[]>({ url: '/api/user/address/list', method: 'get' })
}

export function addAddress(data: unknown) {
  return request<string>({ url: '/api/user/address', method: 'post', data })
}

export function updateAddress(data: unknown) {
  return request<void>({ url: '/api/user/address', method: 'put', data })
}

export function setDefaultAddress(id: string) {
  return request<void>({ url: `/api/user/address/${id}/default`, method: 'put' })
}

export function deleteAddress(id: string) {
  return request<void>({ url: `/api/user/address/${id}`, method: 'delete' })
}

/* ============ 订单 ============ */
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
  receiverCity?: string
  receiverDetail?: string
  expireTime?: string
  createTime?: string
}

export interface OrderDetail {
  order: OrderInfo
  items: OrderItem[]
}

export function getOrderToken() {
  return request<string>({ url: '/api/order/token', method: 'get' })
}

/**
 * 下单：幂等 token 走 **请求头 Idempotent-Token**（网关会预检，服务端 @Idempotent 原子消费）
 */
export function createOrder(
  data: { addressId: string; items: { skuId: string; quantity: number }[]; remark?: string },
  idempotentToken: string
) {
  return request<string>({
    url: '/api/order',
    method: 'post',
    data,
    headers: { 'Idempotent-Token': idempotentToken }
  })
}

export function getOrderPage(params: { pageNum: number; pageSize: number; status?: number }) {
  return request<Page<OrderInfo>>({ url: '/api/order/page', method: 'get', params })
}

export function getOrderDetail(orderNo: string) {
  return request<OrderDetail>({ url: `/api/order/${orderNo}`, method: 'get' })
}

export function cancelOrder(orderNo: string) {
  return request<void>({ url: `/api/order/${orderNo}/cancel`, method: 'post' })
}

/* ============ 支付 ============ */
export interface PaymentVO {
  paymentNo: string
  orderNo: string
  amount: number
}

export function createPayment(orderNo: string) {
  return request<PaymentVO>({ url: '/api/payment/create', method: 'post', data: { orderNo } })
}

export function mockPay(paymentNo: string) {
  return request<void>({ url: '/api/payment/mock-pay', method: 'post', data: { paymentNo } })
}

/* ============ 工具 ============ */
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
