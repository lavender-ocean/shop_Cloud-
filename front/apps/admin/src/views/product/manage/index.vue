<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  type CategoryNode, type Brand, type SpuRow, type SkuRow, type SpuDetail,
  getCategoryTree, getBrandList, getSpuPage, getSpuDetail,
  createSpu, updateSpu, updateSpuStatus, deleteSpu
} from '@/api/product'

const categories = ref<CategoryNode[]>([])
const brands = ref<Brand[]>([])

const query = reactive<{ name: string; categoryId?: string; pageNum: number; pageSize: number }>({
  name: '',
  categoryId: undefined,
  pageNum: 1,
  pageSize: 10
})
const rows = ref<SpuRow[]>([])
const total = ref(0)
const loading = ref(false)

async function loadCategories() {
  categories.value = await getCategoryTree()
}
async function loadBrands() {
  brands.value = await getBrandList()
}
async function loadSpu() {
  loading.value = true
  try {
    const page = await getSpuPage({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      name: query.name || undefined,
      categoryId: query.categoryId
    })
    rows.value = page.list
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
}

function onCategoryClick(node: CategoryNode) {
  query.categoryId = node.id
  query.pageNum = 1
  loadSpu()
}
function resetCategory() {
  query.categoryId = undefined
  query.pageNum = 1
  loadSpu()
}
function search() {
  query.pageNum = 1
  loadSpu()
}

const dialogVisible = ref(false)
const dialogTitle = ref('新增商品')
const form = reactive({
  id: undefined as string | undefined,
  name: '',
  categoryId: undefined as string | undefined,
  brandId: undefined as string | undefined,
  mainImage: '',
  imageText: '',
  detail: '',
  unit: '',
  isColdChain: 0,
  status: 1,
  skus: [] as SkuRow[]
})

function resetForm() {
  form.id = undefined
  form.name = ''
  form.categoryId = undefined
  form.brandId = undefined
  form.mainImage = ''
  form.imageText = ''
  form.detail = ''
  form.unit = ''
  form.isColdChain = 0
  form.status = 1
  form.skus = [{ name: '', specJson: '', price: undefined, status: 1 }]
}

function openCreate() {
  dialogTitle.value = '新增商品'
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row: SpuRow) {
  dialogTitle.value = '编辑商品'
  const d: SpuDetail = await getSpuDetail(row.id)
  form.id = d.id
  form.name = d.name
  form.categoryId = d.categoryId
  form.brandId = d.brandId
  form.mainImage = d.mainImage || ''
  form.imageText = (d.images || []).join('\n')
  form.detail = d.detail || ''
  form.unit = d.unit || ''
  form.isColdChain = d.isColdChain
  form.status = d.status
  form.skus = (d.skus || []).map((s) => ({
    id: s.id, name: s.name, specJson: s.specJson, image: s.image, price: Number(s.price), status: s.status
  }))
  dialogVisible.value = true
}

function addSku() {
  form.skus.push({ name: '', specJson: '', price: undefined, status: 1 })
}
function removeSku(index: number) {
  form.skus.splice(index, 1)
}

async function submit() {
  if (!form.name || !form.categoryId) {
    ElMessage.warning('请填写商品名称与分类')
    return
  }
  if (!form.skus.length) {
    ElMessage.warning('至少需要一个 SKU')
    return
  }
  const payload = {
    id: form.id,
    name: form.name,
    categoryId: form.categoryId,
    brandId: form.brandId,
    mainImage: form.mainImage,
    images: form.imageText ? form.imageText.split('\n').map((s) => s.trim()).filter(Boolean) : [],
    detail: form.detail,
    unit: form.unit,
    isColdChain: form.isColdChain,
    status: form.status,
    skus: form.skus.map((s) => ({
      name: s.name, specJson: s.specJson, image: s.image, price: s.price, status: s.status
    }))
  }
  if (form.id) {
    await updateSpu(payload)
  } else {
    await createSpu(payload)
  }
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadSpu()
}

async function toggleStatus(row: SpuRow) {
  await updateSpuStatus(row.id, row.status === 1 ? 0 : 1)
  ElMessage.success('操作成功')
  loadSpu()
}

async function onDelete(row: SpuRow) {
  await ElMessageBox.confirm(`确认删除「${row.name}」？`, '提示', { type: 'warning' })
  await deleteSpu(row.id)
  ElMessage.success('已删除')
  loadSpu()
}

onMounted(() => {
  loadCategories()
  loadBrands()
  loadSpu()
})
</script>

<template>
  <el-container>
    <el-aside width="220px" style="border-right:1px solid #eee;padding:8px 12px">
      <el-button size="small" @click="resetCategory">全部类目</el-button>
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
          <el-input v-model="query.name" placeholder="商品名称" clearable @keyup.enter="search" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button type="success" @click="openCreate">新增商品</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="name" label="商品名称" min-width="160" />
        <el-table-column prop="unit" label="规格单位" width="100" />
        <el-table-column label="价格区间(元)" width="150">
          <template #default="{ row }">
            {{ (Number(row.priceMin) / 100).toFixed(2) }} ~ {{ (Number(row.priceMax) / 100).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column label="冷链" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isColdChain === 1" type="info">冷链</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" @click="toggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
          style="margin-top:12px"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="(p: number) => { query.pageNum = p; loadSpu() }"
      />
    </el-main>
  </el-container>

  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="780px">
    <el-form label-width="90px">
      <el-form-item label="商品名称"><el-input v-model="form.name" /></el-form-item>
      <el-form-item label="分类">
        <el-tree-select
            v-model="form.categoryId"
            :data="categories"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            check-strictly
            style="width:100%"
        />
      </el-form-item>
      <el-form-item label="品牌">
        <el-select v-model="form.brandId" clearable placeholder="可选" style="width:100%">
          <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="主图URL"><el-input v-model="form.mainImage" /></el-form-item>
      <el-form-item label="图片列表">
        <el-input v-model="form.imageText" type="textarea" :rows="2" placeholder="每行一个图片URL" />
      </el-form-item>
      <el-form-item label="规格单位">
        <el-input v-model="form.unit" placeholder="如 500g / 盒" />
      </el-form-item>
      <el-form-item label="是否冷链">
        <el-switch v-model="form.isColdChain" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
      </el-form-item>
      <el-form-item label="详情">
        <el-input v-model="form.detail" type="textarea" :rows="3" />
      </el-form-item>

      <el-form-item label="SKU">
        <el-button size="small" @click="addSku">添加一行</el-button>
        <el-table :data="form.skus" border size="small" style="margin-top:8px">
          <el-table-column label="SKU名称" min-width="140">
            <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
          </el-table-column>
          <el-table-column label="规格" width="140">
            <template #default="{ row }"><el-input v-model="row.specJson" size="small" /></template>
          </el-table-column>
          <el-table-column label="价格(分)" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" size="small" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeSku($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>
