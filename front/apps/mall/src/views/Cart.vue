<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type CartItem, getCart, updateCartQuantity, updateCartSelected, removeCartItem, yuan
} from '@/api'
import { useCartStore } from '@/stores/cart'

const router = useRouter()
const cartStore = useCartStore()

const items = ref<CartItem[]>([])
const loading = ref(false)

const invalidCount = computed(() => items.value.filter((i) => !i.valid).length)
const selectedItems = computed(() => items.value.filter((i) => i.valid && i.selected))
const selectedAmount = computed(() =>
  selectedItems.value.reduce((sum, i) => sum + Number(i.price) * i.quantity, 0)
)
const selectedCount = computed(() =>
  selectedItems.value.reduce((n, i) => n + i.quantity, 0)
)

async function load() {
  loading.value = true
  try {
    const cart = await getCart()
    items.value = cart.items
    cartStore.setCount(cart.totalQuantity)
  } finally {
    loading.value = false
  }
}

async function changeQuantity(item: CartItem, value: number) {
  await updateCartQuantity({ skuId: item.skuId, quantity: value })
  load()
}

async function changeSelected(item: CartItem, value: boolean) {
  await updateCartSelected({ skuId: item.skuId, selected: value })
  load()
}

async function remove(item: CartItem) {
  await ElMessageBox.confirm(`移除「${item.skuName}」？`, '提示', { type: 'warning' })
  await removeCartItem(item.skuId)
  ElMessage.success('已移除')
  load()
}

async function clearInvalid() {
  const targets = items.value.filter((i) => !i.valid)
  if (targets.length === 0) return
  await ElMessageBox.confirm(`移除 ${targets.length} 件失效商品？`, '提示', { type: 'warning' })
  await Promise.all(targets.map((i) => removeCartItem(i.skuId)))
  ElMessage.success('已清空失效商品')
  load()
}

function checkout() {
  if (selectedCount.value === 0) {
    ElMessage.warning('请先勾选商品')
    return
  }
  router.push('/checkout')
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading">
    <div style="display:flex;justify-content:space-between;align-items:center">
      <h3>购物车</h3>
      <el-button v-if="invalidCount > 0" link type="danger" @click="clearInvalid">
        清空失效商品（{{ invalidCount }}）
      </el-button>
    </div>

    <el-table :data="items" border>
      <el-table-column label="选择" width="70">
        <template #default="{ row }">
          <el-checkbox :model-value="row.selected" :disabled="!row.valid"
                       @change="(v: boolean) => changeSelected(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="商品" min-width="240">
        <template #default="{ row }">
          <div style="display:flex;align-items:center;gap:10px"
               :style="row.valid ? '' : 'opacity:.5'">
            <el-image v-if="row.image" :src="row.image" style="width:48px;height:48px" fit="contain" />
            <div>
              <div>
                {{ row.skuName }}
                <el-tag v-if="!row.valid" size="small" type="info" style="margin-left:6px">
                  {{ row.invalidReason || '已失效' }}
                </el-tag>
              </div>
              <div style="color:#909399;font-size:12px">{{ row.spuName }} {{ row.specJson }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="单价" width="110">
        <template #default="{ row }">
          <span v-if="row.valid">￥{{ yuan(row.price) }}</span>
          <span v-else style="color:#c0c4cc">--</span>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="150">
        <template #default="{ row }">
          <el-input-number :model-value="row.quantity" :min="1" :max="99" size="small"
                           :disabled="!row.valid"
                           @change="(v: number) => changeQuantity(row, v)" />
        </template>
      </el-table-column>
      <el-table-column label="小计" width="120">
        <template #default="{ row }">
          <span v-if="row.valid" style="color:#f56c6c">
            ￥{{ yuan(Number(row.price) * row.quantity) }}
          </span>
          <span v-else style="color:#c0c4cc">--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display:flex;justify-content:flex-end;align-items:center;gap:20px;margin-top:16px">
      <span>已选 {{ selectedCount }} 件</span>
      <span>合计：<span style="color:#f56c6c;font-size:18px;font-weight:700">￥{{ yuan(selectedAmount) }}</span></span>
      <el-button type="danger" size="large" @click="checkout">去结算</el-button>
    </div>

    <el-empty v-if="!loading && items.length === 0" description="购物车是空的" />
  </el-card>
</template>
