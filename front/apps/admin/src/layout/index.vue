<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import MenuTree from '@/components/MenuTree.vue'

const route = useRoute()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const activeMenu = computed(() => route.path)
</script>

<template>
  <el-container style="height:100vh">
    <el-aside width="220px" style="background:#001529">
      <div style="color:#fff;height:56px;line-height:56px;text-align:center">shop-cloud</div>
      <el-menu :default-active="activeMenu" router background-color="#001529"
               text-color="#bfcbd9" active-text-color="#409eff">
        <MenuTree v-for="m in permissionStore.menus" :key="m.id" :menu="m" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="display:flex;justify-content:space-between;align-items:center">
        <span>{{ route.meta.title }}</span>
        <span>
          {{ userStore.username }}（{{ userStore.roles.join(',') }}）
          <el-button link type="primary" @click="userStore.logout()">退出</el-button>
        </span>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>