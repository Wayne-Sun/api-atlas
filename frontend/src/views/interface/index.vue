<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import type { VNode } from 'vue'
import { useRouter } from 'vue-router'
import { useInterfaceStore } from '@/stores/interface'
import type { ApiInterface } from '@/stores/interface'
import { useAuthStore } from '@/stores/auth'
import { NButton, NDataTable, NTag, NSpace, NInput, NSelect, NEmpty, NCard, NPopconfirm, useMessage } from 'naive-ui'

const router = useRouter()
const store = useInterfaceStore()
const authStore = useAuthStore()
const message = useMessage()
const searchName = ref('')
const searchStatus = ref<string | null>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const deletingId = ref<number | null>(null)

const statusOptions = [
  { label: '待测试', value: 'PENDING_TEST' },
  { label: '已上线', value: 'ONLINE' },
  { label: '已下线', value: 'OFFLINE' }
]

const statusTypeMap: Record<string, 'warning' | 'success' | 'default'> = {
  'PENDING_TEST': 'warning',
  'ONLINE': 'success',
  'OFFLINE': 'default'
}

const statusLabelMap: Record<string, string> = {
  'PENDING_TEST': '待测试',
  'ONLINE': '已上线',
  'OFFLINE': '已下线'
}

const columns = computed(() => {
  const baseColumns = [
    { title: '中文名称', key: 'chineseName' },
    { title: '英文名称', key: 'englishName' },
    { title: '所属数据源', key: 'dataSourceName' },
    { title: '方法', key: 'method' },
    {
      title: '状态',
      key: 'status',
      render: (row: ApiInterface) => h(NTag, { type: statusTypeMap[row.status] || 'default' }, { default: () => statusLabelMap[row.status] || row.status })
    },
    { title: '创建时间', key: 'createdAt' }
  ]

  if (authStore.isAdmin) {
    baseColumns.push({
      title: '操作',
      key: 'actions',
      render: (row: ApiInterface) => {
        const buttons: VNode[] = []

        // Edit button for PENDING_TEST and OFFLINE
        if (row.status === 'PENDING_TEST' || row.status === 'OFFLINE') {
          buttons.push(h(NButton, { size: 'small', onClick: () => router.push(`/interface/edit/${row.id}`) }, { default: () => '编辑' }))
        }

        // Test button for PENDING_TEST and ONLINE
        if (row.status === 'PENDING_TEST' || row.status === 'ONLINE') {
          buttons.push(h(NButton, { size: 'small', onClick: () => router.push(`/interface/test/${row.id}`) }, { default: () => '测试' }))
        }

        // Online/Offline toggle
        if (row.status === 'PENDING_TEST' || row.status === 'OFFLINE') {
          buttons.push(h(NButton, { size: 'small', type: 'primary', onClick: () => handleOnline(row) }, { default: () => '上线' }))
        }
        if (row.status === 'ONLINE') {
          buttons.push(h(NButton, { size: 'small', type: 'warning', onClick: () => handleOffline(row) }, { default: () => '下线' }))
        }

        // Delete button (all statuses)
        buttons.push(
          h(NPopconfirm, {
            onPositiveClick: () => handleDelete(row)
          }, {
            trigger: () => h(NButton, { size: 'small', type: 'error', loading: deletingId.value === row.id }, { default: () => '删除' }),
            default: () => '确定删除？'
          })
        )

        return h(NSpace, null, { default: () => buttons })
      }
    })
  }

  return baseColumns
})

async function handleOnline(row: ApiInterface) {
  try {
    await store.updateStatus(row.id, 'ONLINE')
    message.success('上线成功')
    await fetchData()
  } catch (e) {
    console.warn('接口上线失败:', e)
    // handled by interceptor
  }
}

async function handleOffline(row: ApiInterface) {
  try {
    await store.updateStatus(row.id, 'OFFLINE')
    message.success('下线成功')
    await fetchData()
  } catch (e) {
    console.warn('接口下线失败:', e)
    // handled by interceptor
  }
}

async function handleDelete(row: ApiInterface) {
  deletingId.value = row.id
  try {
    await store.remove(row.id)
    message.success('删除成功')
    await fetchData()
  } catch (e) {
    console.warn('删除接口失败:', e)
    // Error already handled by Axios interceptor
  } finally {
    deletingId.value = null
  }
}

async function fetchData() {
  await store.fetchList({
    name: searchName.value || undefined,
    status: searchStatus.value || undefined,
    pageNum: currentPage.value,
    pageSize: pageSize.value
  })
}

function handleSearch() {
  currentPage.value = 1
  fetchData()
}

onMounted(fetchData)
</script>

<template>
  <NCard title="接口管理">
    <template #header-extra>
      <NButton v-if="authStore.isAdmin" type="primary" @click="router.push('/interface/create')">新增接口</NButton>
    </template>
    <NSpace style="margin-bottom: 16px;">
      <NInput v-model:value="searchName" placeholder="搜索名称" clearable style="width: 200px" />
      <NSelect v-model:value="searchStatus" :options="statusOptions" placeholder="状态筛选" clearable style="width: 150px" />
      <NButton @click="handleSearch">搜索</NButton>
    </NSpace>
    <NDataTable
      :columns="columns"
      :data="store.list"
      :loading="store.loading"
      :pagination="{
        page: currentPage,
        pageSize: pageSize,
        itemCount: store.total,
        onChange: (page: number) => { currentPage = page; fetchData() },
        'onUpdate:pageSize': (size: number) => { pageSize = size; fetchData() }
      }"
      :empty="() => h(NEmpty, { description: '暂无接口' })"
    />
  </NCard>
</template>
