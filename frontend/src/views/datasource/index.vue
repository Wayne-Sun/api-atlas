<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDatasourceStore } from '@/stores/datasource'
import type { DataSource } from '@/stores/datasource'
import { NButton, NDataTable, NTag, NSwitch, NSpace, NInput, NSelect, NEmpty, NCard, NPopconfirm, useMessage } from 'naive-ui'

const router = useRouter()
const store = useDatasourceStore()
const message = useMessage()
const searchName = ref('')
const searchType = ref<string | null>(null)
const currentPage = ref(1)
const pageSize = ref(10)
const deletingId = ref<number | null>(null)

const typeOptions = [
  { label: 'MySQL', value: 'MySQL' },
  { label: 'PostgreSQL', value: 'PostgreSQL' },
  { label: 'Elasticsearch', value: 'Elasticsearch' }
]

const columns = [
  { title: '名称', key: 'name' },
  {
    title: '类型',
    key: 'type',
    render: (row: DataSource) => h(NTag, {
      type: row.type === 'MySQL' ? 'info' as const : row.type === 'PostgreSQL' ? 'success' as const : 'warning' as const
    }, { default: () => row.type })
  },
  { title: '主机', key: 'host' },
  { title: '端口', key: 'port' },
  {
    title: '状态',
    key: 'status',
    render: (row: DataSource) => h(NSwitch, {
      value: row.status === 'ENABLED',
      'onUpdate:value': () => handleToggle(row)
    })
  },
  { title: '创建时间', key: 'createdAt' },
  {
    title: '操作',
    key: 'actions',
    render: (row: DataSource) => h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', onClick: () => router.push(`/datasource/edit/${row.id}`) }, { default: () => '编辑' }),
        h(NPopconfirm, { onPositiveClick: () => handleDelete(row) }, { trigger: () => h(NButton, { size: 'small', type: 'error' as const, loading: deletingId.value === row.id }, { default: () => '删除' }), default: () => '确定删除？' })
      ]
    })
  }
]

async function handleToggle(row: DataSource) {
  try {
    const newStatus = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    await store.toggleStatus(row.id, newStatus)
    await fetchData()
  } catch (e) {
    console.warn('切换数据源状态失败:', e)
    // handled by interceptor
  }
}

async function handleDelete(row: DataSource) {
  deletingId.value = row.id
  try {
    await store.remove(row.id)
    await fetchData()
  } catch (e) {
    console.warn('删除数据源失败:', e)
    // Error already handled by Axios interceptor
  } finally {
    deletingId.value = null
  }
}

async function fetchData() {
  await store.fetchList({
    name: searchName.value || undefined,
    type: searchType.value || undefined,
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
  <NCard title="数据源管理">
    <template #header-extra>
      <NButton type="primary" @click="router.push('/datasource/create')">新增数据源</NButton>
    </template>
    <NSpace style="margin-bottom: 16px;">
      <NInput v-model:value="searchName" placeholder="搜索名称" clearable style="width: 200px" />
      <NSelect v-model:value="searchType" :options="typeOptions" placeholder="类型筛选" clearable style="width: 150px" />
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
    >
      <template #empty>
        <NEmpty description="暂无数据源" />
      </template>
    </NDataTable>
  </NCard>
</template>
