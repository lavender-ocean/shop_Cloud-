<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type OrderInfo, type OrderDetail,
  getOrderPage, getOrderDetail, shipOrder,
  yuan, ORDER_STATUS_TEXT, ORDER_STATUS_TYPE
} from '@/api/order'

const rows = ref<OrderInfo[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive<{ status?: number; pageNum: number; pageSize: number }>({
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

async function load() {
  loading.value = true
  try {
    const page = await getOrderPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      status: query.status
    })
    rows.value = page.list
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function filterStatus(status?: number) {
  query.status = status
  query.pageNum = 1
  load()
}

/* ===== 详情 ===== */
const dialogVisible = ref(false)
const detail = ref<OrderDetail | null>(null)

async function openDetail(row: OrderInfo) {
  detail.value = await getOrderDetail(row.orderNo)
  dialogVisible.value = true
}

async function ship(row: OrderInfo) {
  await ElMessageBox.confirm(`确认对订单「${row.orderNo}」发货？`, '提示', { type: 'warning' })
  await shipOrder(row.orderNo)
  ElMessage.success('发货成功')
  dialogVisible.value = false
  load()
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading">
    <div style="display:flex;justify-content:space-between;align-items:center">
      <h3>订单管理</h3>
      <el-radio-group :model-value="query.status" @change="(v: any) => filterStatus(v === '' ? undefined : v)">
        <el-radio-button :value="undefined">全部</el-radio-button>
        <el-radio-button :value="0">待支付</el-radio-button>
        <el-radio-button :value="1">待发货</el-radio-button>
        <el-radio-button :value="2">配送中</el-radio-button>
        <el-radio-button :value="3">已完成</el-radio-button>
        <el-radio-button :value="5">已关闭</el-radio-button>
      </el-radio-group>
    </div>

    <el-table :data="rows" border style="margin-top:12px">
      <el-table-column prop="orderNo" label="订单号" min-width="200" />
      <el-table-column label="收货人" width="160">
        <template #default="{ row }">{{ row.receiverName }} {{ row.receiverPhone }}</template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">￥{{ yuan(row.payAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="ORDER_STATUS_TYPE[row.status]">{{ ORDER_STATUS_TEXT[row.status] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="180" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
              v-if="row.status === 1"
              v-permission="'order:ship'"
              link
              type="success"
              @click="ship(row)"
          >
            发货
          </el-button>
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

  <el-dialog v-model="dialogVisible" title="订单详情" width="760px">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="订单号">{{ detail.order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="ORDER_STATUS_TYPE[detail.order.status]">
            {{ ORDER_STATUS_TEXT[detail.order.status] }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="收货人">
          {{ detail.order.receiverName }} {{ detail.order.receiverPhone }}
        </el-descriptions-item>
        <el-descriptions-item label="收货地址">
          {{ detail.order.receiverProvince }}{{ detail.order.receiverCity }}{{ detail.order.receiverDistrict }}{{ detail.order.receiverDetail }}
        </el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ detail.order.createTime }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ detail.order.payTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.order.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="detail.items" border size="small" style="margin-top:16px">
        <el-table-column prop="skuName" label="商品" min-width="180" />
        <el-table-column prop="specJson" label="规格" width="120" />
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

      <div style="display:flex;justify-content:flex-end;margin-top:12px">
        应付：<span style="color:#f56c6c;font-weight:700;margin-left:4px">￥{{ yuan(detail.order.payAmount) }}</span>
      </div>
    </template>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
      <el-button
          v-if="detail && detail.order.status === 1"
          v-permission="'order:ship'"
          type="primary"
          @click="ship(detail.order)"
      >
        发货
      </el-button>
    </template>
  </el-dialog>
</template>