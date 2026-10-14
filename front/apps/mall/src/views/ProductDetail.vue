<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { type SpuDetail, type Sku, getSpuDetail, addToCart, yuan } from '@/api'
import { useCartStore } from '@/stores/cart'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()

const detail = ref<SpuDetail | null>(null)
const skuId = ref<string>('')
const quantity = ref(1)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    detail.value = await getSpuDetail(String(route.params.id))
    if (detail.value?.skus?.length) {
      skuId.value = detail.value.skus[0].id
    }
  } finally {
    loading.value = false
  }
}

function currentSku(): Sku | undefined {
  return detail.value?.skus.find((s) => s.id === skuId.value)
}

async function onAddCart() {
  if (!skuId.value) {
    ElMessage.warning('请选择规格')
    return
  }
  await addToCart({ skuId: skuId.value, quantity: quantity.value })
  await cartStore.refresh()
  ElMessage.success('已加入购物车')
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading">
    <div v-if="detail" style="display:flex;gap:24px">
      <div style="width:300px;height:300px;background:#fafafa;display:flex;align-items:center;justify-content:center">
        <el-image v-if="detail.mainImage" :src="detail.mainImage" fit="contain" style="width:300px;height:300px" />
        <span v-else style="color:#c0c4cc">暂无图片</span>
      </div>

      <div style="flex:1">
        <h2>{{ detail.name }}</h2>
        <div style="color:#f56c6c;font-size:22px;font-weight:700;margin:12px 0">
          ￥{{ yuan(currentSku()?.price ?? detail.priceMin) }}
        </div>
        <div style="color:#909399;font-size:13px;margin-bottom:12px">
          <span v-if="detail.unit">规格单位：{{ detail.unit }}</span>
          <el-tag v-if="detail.isColdChain === 1" size="small" type="info" style="margin-left:8px">冷链配送</el-tag>
        </div>

        <el-form label-width="70px">
          <el-form-item label="规格">
            <el-radio-group v-model="skuId">
              <el-radio-button v-for="sku in detail.skus" :key="sku.id" :value="sku.id">
                {{ sku.specJson || sku.name }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="数量">
            <el-input-number v-model="quantity" :min="1" :max="99" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="onAddCart">加入购物车</el-button>
            <el-button @click="router.push('/cart')">去购物车</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <el-divider />
    <h3>商品详情</h3>
    <div style="color:#606266;white-space:pre-wrap">{{ detail?.detail || '暂无详情' }}</div>
  </el-card>
</template>
