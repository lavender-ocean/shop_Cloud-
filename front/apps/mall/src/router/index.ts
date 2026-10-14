import { createRouter, createWebHistory } from 'vue-router'
import { tokenStore } from '@shared/request'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/Home.vue'), meta: { title: '首页' } },
      { path: 'product/:id', name: 'ProductDetail', component: () => import('@/views/ProductDetail.vue'), meta: { title: '商品详情' } },
      { path: 'cart', name: 'Cart', component: () => import('@/views/Cart.vue'), meta: { title: '购物车' } },
      { path: 'checkout', name: 'Checkout', component: () => import('@/views/Checkout.vue'), meta: { title: '确认订单' } },
      { path: 'orders', name: 'OrderList', component: () => import('@/views/OrderList.vue'), meta: { title: '我的订单' } },
      { path: 'order/:orderNo', name: 'OrderDetail', component: () => import('@/views/OrderDetail.vue'), meta: { title: '订单详情' } },
      { path: 'address', name: 'Address', component: () => import('@/views/Address.vue'), meta: { title: '收货地址' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!tokenStore.access) return { path: '/login' }
  return true
})

export default router
