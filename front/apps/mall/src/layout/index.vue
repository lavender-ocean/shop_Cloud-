<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const userStore = useUserStore()
const cartStore = useCartStore()

onMounted(() => cartStore.refresh())

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <el-container style="min-height:100vh;background:#f5f7fa">
    <el-header style="background:#fff;display:flex;align-items:center;justify-content:space-between;box-shadow:0 1px 4px rgba(0,0,0,.06)">
      <div style="font-weight:700;font-size:18px;color:#409eff;cursor:pointer" @click="go('/home')">
        shop-cloud 线上超市
      </div>
      <div style="display:flex;align-items:center;gap:18px">
        <span style="cursor:pointer" @click="go('/home')">首页</span>
        <el-badge :value="cartStore.count" :hidden="cartStore.count === 0">
          <span style="cursor:pointer" @click="go('/cart')">购物车</span>
        </el-badge>
        <span style="cursor:pointer" @click="go('/orders')">我的订单</span>
        <span style="cursor:pointer" @click="go('/address')">收货地址</span>
        <span style="color:#909399">{{ userStore.username }}</span>
        <el-button link type="primary" @click="userStore.logout()">退出</el-button>
      </div>
    </el-header>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>
