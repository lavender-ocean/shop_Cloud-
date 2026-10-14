<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type OrderDetail, getOrderDetail, cancelOrder, createPayment, mockPay,
  yuan, ORDER_STATUS_TEXT, ORDER_STATUS_TYPE
} from '@/api'

const route = useRoute()
const router = useRouter()
const detail = ref<OrderDetail | null>(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    detail.value = await getOrderDetail(String(route.params.orderNo))
  } finally {
    loading.value = false
  }
}

async function pay() {
  if (!detail.value) return
  const payment = await createPayment(detail.value.order.orderNo)
  await mockPay(payment.paymentNo)
  ElMessage.success('支付成功')
  load()
}

async function cancel() {
  if (!detail.value) return
  await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
  await cancelOrder(detail.value.order.orderNo)
  ElMessage.success('已取消')
  load()
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading">
    <template v-if="detail">
      <div style="display:flex;justify-content:space-between;align-items:center">
        <h3>订单详情</h3>
        <el-tag :type="ORDER_STATUS_TYPE[detail.order.status]">
          {{ ORDER_STATUS_TEXT[detail.order.status] }}
        </el-tag>
      </div>

      <el-descriptions :column="2" border style="margin-top:12px">
        <el-descriptions-item label="订单号">{{ detail.order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ detail.order.createTime }}</el-descriptions-item>
        <el-descriptions-item label="收货人">{{ detail.order.receiverName }} {{ detail.order.receiverPhone }}</el-descriptions-item>
        <el-descriptions-item label="收货地址">
          {{ detail.order.receiverCity }}{{ detail.order.receiverDetail }}
        </el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detail.order.payTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="超时时间">{{ detail.order.expireTime }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small" style="margin-top:16px">
        <el-table-column prop="skuName" label="商品" min-width="200" />
        <el-table-column label="单价" width="110">
          <template #default="{ row }">￥{{ yuan(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="小计" width="120">
          <template #default="{ row }">
            <span style="color:#f56c6c">￥{{ yuan(row.totalAmount) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div style="display:flex;justify-content:flex-end;align-items:center;gap:20px;margin-top:16px">
        <span>应付：<span style="color:#f56c6c;font-size:20px;font-weight:700">￥{{ yuan(detail.order.payAmount) }}</span></span>
        <el-button v-if="detail.order.status === 0" type="danger" @click="pay">去支付（Mock）</el-button>
        <el-button v-if="detail.order.status === 0" @click="cancel">取消订单</el-button>
        <el-button @click="router.push('/orders')">返回列表</el-button>
      </div>
    </template>
  </el-card>
</template>
