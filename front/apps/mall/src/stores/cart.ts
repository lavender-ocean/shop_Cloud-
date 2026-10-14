import { defineStore } from 'pinia'
import { getCart } from '@/api'

export const useCartStore = defineStore('cart', {
  state: () => ({
    count: 0
  }),
  actions: {
    async refresh() {
      try {
        const cart = await getCart()
        this.count = cart.totalQuantity
      } catch {
        this.count = 0
      }
    },
    /** 已在别处拿到购物车数据时直接写入，避免重复请求 */
    setCount(count: number) {
      this.count = count
    }
  }
})
