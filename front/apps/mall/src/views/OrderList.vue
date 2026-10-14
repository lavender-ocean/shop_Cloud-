<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type OrderInfo, getOrderPage, cancelOrder, createPayment, mockPay,
  yuan, ORDER_STATUS_TEXT, ORDER_STATUS_TYPE
} from '@/api'

const router = useRouter()
const rows = ref<OrderInfo[]>([])
const total = ref(0)
const loading = ref(false)
const query = ref<{ status?: number; pageNum: number; pageSize: number }>({
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const page = await getOrderPage(query.value)
    rows.value = page.list
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function filterStatus(status?: number) {
  query.value.status = status
  query.value.pageNum = 1
  load()
}

async function pay(row: OrderInfo) {
  const payment = await createPayment(row.orderNo)
  await mockPay(payment.paymentNo)
  ElMessage.success('支付成功')
  load()
}

async function cancel(row: OrderInfo) {
  await ElMessageBox.confirm('确认取消该订单？', '提示', { type: 'warning' })
  await cancelOrder(row.orderNo)
  ElMessage.success('已取消')
  load()
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading">
    <h3>我的订单</h3>
    <el-radio-group :model-value="query.status" style="margin-bottom:12px"
                    @change="(v: any) => filterStatus(v === '' ? undefined : v)">
      <el-radio-button :value="undefined">全部</el-radio-button>
      <el-radio-button :value="0">待支付</el-radio-button>
      <el-radio-button :value="1">待发货</el-radio-button>
      <el-radio-button :value="2">配送中</el-radio-button>
      <el-radio-button :value="3">已完成</el-radio-button>
    </el-radio-group>

    <el-table :data="rows" border>
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column label="金额" width="120">
        <template #default="{ row }">￥{{ yuan(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="ORDER_STATUS_TYPE[row.status]">{{ ORDER_STATUS_TEXT[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="180" />
      <el-table-column label="操作" width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="router.push(`/order/${row.orderNo}`)">详情</el-button>
          <el-button v-if="row.status === 0" link type="success" @click="pay(row)">去支付</el-button>
          <el-button v-if="row.status === 0" link type="danger" @click="cancel(row)">取消</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && rows.length === 0" description="暂无订单" />

    <el-pagination
      style="margin-top:16px;justify-content:center"
      layout="total, prev, pager, next"
      :total="total"
      :page-size="query.pageSize"
      :current-page="query.pageNum"
      @current-change="(p: number) => { query.pageNum = p; load() }"
    />
  </el-card>
</template>
