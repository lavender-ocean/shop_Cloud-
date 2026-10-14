<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { type CategoryNode, type Spu, getCategoryTree, getSpuPage, yuan } from '@/api'

const router = useRouter()
const categories = ref<CategoryNode[]>([])
const rows = ref<Spu[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref<{ name: string; categoryId?: string; pageNum: number; pageSize: number }>({
  name: '',
  categoryId: undefined,
  pageNum: 1,
  pageSize: 12
})

async function loadCategories() {
  categories.value = await getCategoryTree()
}
async function loadSpu() {
  loading.value = true
  try {
    const page = await getSpuPage({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      name: query.value.name || undefined,
      categoryId: query.value.categoryId
    })
    rows.value = page.list
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}
function onCategoryClick(node: CategoryNode) {
  query.value.categoryId = node.id
  query.value.pageNum = 1
  loadSpu()
}
function resetCategory() {
  query.value.categoryId = undefined
  query.value.pageNum = 1
  loadSpu()
}
function search() {
  query.value.pageNum = 1
  loadSpu()
}

onMounted(() => {
  loadCategories()
  loadSpu()
})
</script>

<template>
  <el-container>
    <el-aside width="200px" style="background:#fff;padding:12px;border-radius:6px">
      <el-button size="small" style="width:100%" @click="resetCategory">全部类目</el-button>
      <el-tree
        :data="categories"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        style="margin-top:8px"
        @node-click="onCategoryClick"
      />
    </el-aside>

    <el-main>
      <el-form inline>
        <el-form-item>
          <el-input v-model="query.name" placeholder="搜索商品" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
        </el-form-item>
      </el-form>

      <div v-loading="loading" style="display:flex;flex-wrap:wrap;gap:16px">
        <el-card
          v-for="spu in rows"
          :key="spu.id"
          style="width:220px;cursor:pointer"
          :body-style="{ padding: '10px' }"
          @click="router.push(`/product/${spu.id}`)"
        >
          <div style="height:140px;display:flex;align-items:center;justify-content:center;background:#fafafa">
            <el-image v-if="spu.mainImage" :src="spu.mainImage" fit="contain" style="height:140px" />
            <span v-else style="color:#c0c4cc">暂无图片</span>
          </div>
          <div style="margin-top:8px;font-size:14px;height:40px;overflow:hidden">{{ spu.name }}</div>
          <div style="color:#f56c6c;font-weight:700">￥{{ yuan(spu.priceMin) }} 起</div>
          <div style="font-size:12px;color:#909399">
            <span v-if="spu.unit">{{ spu.unit }}</span>
            <el-tag v-if="spu.isColdChain === 1" size="small" type="info" style="margin-left:6px">冷链</el-tag>
          </div>
        </el-card>
      </div>

      <el-empty v-if="!loading && rows.length === 0" description="暂无商品" />

      <el-pagination
        style="margin-top:16px;justify-content:center"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.pageNum"
        @current-change="(p: number) => { query.pageNum = p; loadSpu() }"
      />
    </el-main>
  </el-container>
</template>
