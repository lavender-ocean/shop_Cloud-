<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  type Address, type CartItem,
  getAddressList, getCart, getOrderToken, createOrder, yuan
} from '@/api'

const router = useRouter()
const addresses = ref<Address[]>([])
const items = ref<CartItem[]>([])
const addressId = ref<string>('')
const remark = ref('')
const submitting = ref(false)

const selectedAmount = computed(() =>
  items.value.reduce((sum, i) => sum + Number(i.price) * i.quantity, 0)
)

async function load() {
  const list = await getAddressList()
  addresses.value = list
  const def = list.find((a) => a.isDefault === 1) || list[0]
  if (def) addressId.value = def.id

  const cart = await getCart()
  items.value = cart.items.filter((i) => i.selected && i.valid)
}

async function submit() {
  if (!addressId.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (items.value.length === 0) {
    ElMessage.warning('没有可结算的商品')
    return
  }
  submitting.value = true
  try {
    const token = await getOrderToken()
    const orderNo = await createOrder({
      addressId: addressId.value,
      items: items.value.map((i) => ({ skuId: i.skuId, quantity: i.quantity })),
      remark: remark.value || undefined
    }, token)
    ElMessage.success('下单成功，请尽快支付')
    router.replace(`/order/${orderNo}`)
  } catch (e: any) {
    ElMessage.error(e.message || '下单失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <el-card>
    <h3>确认订单</h3>

    <el-divider content-position="left">收货地址</el-divider>
    <el-radio-group v-model="addressId" style="display:flex;flex-direction:column;gap:8px">
      <el-radio v-for="a in addresses" :key="a.id" :value="a.id" border>
        {{ a.receiverName }} {{ a.receiverPhone }} · {{ a.province }}{{ a.city }}{{ a.district }}{{ a.detailAddress }}
        <el-tag v-if="a.isDefault === 1" size="small" type="success" style="margin-left:6px">默认</el-tag>
      </el-radio>
    </el-radio-group>
    <el-empty v-if="addresses.length === 0" description="还没有收货地址">
      <el-button type="primary" @click="router.push('/address')">去添加</el-button>
    </el-empty>

    <el-divider content-position="left">商品清单</el-divider>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom:8px"
              title="商品价格实时取自商品服务，最终以提交订单时为准" />
    <el-table :data="items" border size="small">
      <el-table-column prop="skuName" label="商品" min-width="200" />
      <el-table-column label="单价" width="110">
        <template #default="{ row }">￥{{ yuan(row.price) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column label="小计" width="120">
        <template #default="{ row }">
          <span style="color:#f56c6c">￥{{ yuan(Number(row.price) * row.quantity) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-divider content-position="left">备注</el-divider>
    <el-input v-model="remark" placeholder="选填" maxlength="100" />

    <div style="display:flex;justify-content:flex-end;align-items:center;gap:20px;margin-top:20px">
      <span>应付：<span style="color:#f56c6c;font-size:20px;font-weight:700">￥{{ yuan(selectedAmount) }}</span></span>
      <el-button type="danger" size="large" :loading="submitting" @click="submit">提交订单</el-button>
    </div>
  </el-card>
</template>
