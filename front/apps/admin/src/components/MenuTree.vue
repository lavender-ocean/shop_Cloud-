<script setup lang="ts">
import type { MenuVO } from '@shared/types'
import { iconMap } from '@/utils/icons'

defineProps<{ menu: MenuVO }>()
</script>

<template>
  <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path">
    <template #title>
      <el-icon v-if="menu.icon"><component :is="iconMap[menu.icon]" /></el-icon>
      <span>{{ menu.name }}</span>
    </template>
    <MenuTree v-for="c in menu.children" :key="c.id" :menu="c" />
  </el-sub-menu>
  <el-menu-item v-else :index="menu.path">
    <el-icon v-if="menu.icon"><component :is="iconMap[menu.icon]" /></el-icon>
    <span>{{ menu.name }}</span>
  </el-menu-item>
</template>