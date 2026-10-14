<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type Address, getAddressList, addAddress, updateAddress, setDefaultAddress, deleteAddress
} from '@/api'

const list = ref<Address[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogTitle = ref('新增地址')
const form = reactive({
  id: undefined as string | undefined,
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  tag: '',
  isDefault: 0
})

async function load() {
  loading.value = true
  try {
    list.value = await getAddressList()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = undefined
  form.receiverName = ''
  form.receiverPhone = ''
  form.province = ''
  form.city = ''
  form.district = ''
  form.detailAddress = ''
  form.tag = ''
  form.isDefault = 0
}

function openCreate() {
  dialogTitle.value = '新增地址'
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: Address) {
  dialogTitle.value = '编辑地址'
  form.id = row.id
  form.receiverName = row.receiverName
  form.receiverPhone = row.receiverPhone
  form.province = row.province || ''
  form.city = row.city || ''
  form.district = row.district || ''
  form.detailAddress = row.detailAddress
  form.tag = row.tag || ''
  form.isDefault = row.isDefault
  dialogVisible.value = true
}

async function submit() {
  if (!form.receiverName || !form.receiverPhone || !form.detailAddress) {
    ElMessage.warning('请填写收货人、手机号、详细地址')
    return
  }
  if (form.id) {
    await updateAddress({ ...form })
  } else {
    await addAddress({ ...form })
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  load()
}

async function setDefault(row: Address) {
  await setDefaultAddress(row.id)
  ElMessage.success('已设为默认')
  load()
}

async function remove(row: Address) {
  await ElMessageBox.confirm('确认删除该地址？', '提示', { type: 'warning' })
  await deleteAddress(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <el-card v-loading="loading">
    <div style="display:flex;justify-content:space-between;align-items:center">
      <h3>收货地址</h3>
      <el-button type="primary" @click="openCreate">新增地址</el-button>
    </div>

    <el-table :data="list" border style="margin-top:12px">
      <el-table-column prop="receiverName" label="收货人" width="120" />
      <el-table-column prop="receiverPhone" label="手机号" width="140" />
      <el-table-column label="地址" min-width="240">
        <template #default="{ row }">
          {{ row.province }}{{ row.city }}{{ row.district }}{{ row.detailAddress }}
        </template>
      </el-table-column>
      <el-table-column prop="tag" label="标签" width="90" />
      <el-table-column label="默认" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
          <el-button v-else link type="primary" @click="setDefault(row)">设为默认</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && list.length === 0" description="还没有收货地址" />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form label-width="90px">
      <el-form-item label="收货人"><el-input v-model="form.receiverName" /></el-form-item>
      <el-form-item label="手机号"><el-input v-model="form.receiverPhone" /></el-form-item>
      <el-form-item label="省"><el-input v-model="form.province" /></el-form-item>
      <el-form-item label="市"><el-input v-model="form.city" /></el-form-item>
      <el-form-item label="区"><el-input v-model="form.district" /></el-form-item>
      <el-form-item label="详细地址"><el-input v-model="form.detailAddress" /></el-form-item>
      <el-form-item label="标签"><el-input v-model="form.tag" placeholder="家 / 公司" /></el-form-item>
      <el-form-item label="设为默认">
        <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
