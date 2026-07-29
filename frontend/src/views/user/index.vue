<script setup lang="ts">
import { h, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/stores/user'
import UserFormModal from './components/UserFormModal.vue'
import { NButton, NDataTable, NTag, NSpace, NEmpty, NCard, NPopconfirm, useMessage } from 'naive-ui'

const store = useUserStore()
const message = useMessage()
const deletingId = ref<number | null>(null)
const showModal = ref(false)
const editingUser = ref<UserInfo | null>(null)

const columns = [
  { title: 'ID', key: 'id', width: 80 },
  { title: '用户名', key: 'username' },
  { title: '显示名称', key: 'displayName' },
  {
    title: '角色',
    key: 'role',
    render: (row: UserInfo) => h(NTag, {
      type: row.role === 'ADMIN' ? 'info' as const : 'default' as const,
    }, { default: () => row.role === 'ADMIN' ? '管理员' : '普通用户' })
  },
  {
    title: '状态',
    key: 'status',
    render: (row: UserInfo) => h(NTag, {
      type: row.status === 'ENABLED' ? 'success' as const : 'warning' as const,
    }, { default: () => row.status === 'ENABLED' ? '启用' : '禁用' })
  },
  { title: '创建时间', key: 'createdAt' },
  {
    title: '操作',
    key: 'actions',
    render: (row: UserInfo) => h(NSpace, null, {
      default: () => [
        h(NButton, {
          size: 'small',
          tertiary: true,
          onClick: () => handleEdit(row),
        }, { default: () => '编辑' }),
        h(NPopconfirm, {
          onPositiveClick: () => handleDelete(row),
        }, {
          trigger: () => h(NButton, {
            size: 'small',
            tertiary: true,
            loading: deletingId.value === row.id,
          }, { default: () => '删除' }),
          default: () => '确定删除？',
        }),
      ]
    })
  },
]

function handleEdit(row: UserInfo) {
  editingUser.value = row
  showModal.value = true
}

function handleCreate() {
  editingUser.value = null
  showModal.value = true
}

async function handleDelete(row: UserInfo) {
  deletingId.value = row.id
  try {
    await store.deleteUser(row.id)
    message.success('删除成功')
    await fetchData()
  } catch (e) {
    console.warn('删除用户失败:', e)
    // handled by interceptor
  } finally {
    deletingId.value = null
  }
}

function handleSaved() {
  fetchData()
}

async function fetchData() {
  await store.fetchUsers({
    page: store.pagination.page,
    pageSize: store.pagination.pageSize,
  })
}

onMounted(fetchData)
</script>

<template>
  <NCard title="用户管理">
    <template #header-extra>
      <NButton type="primary" @click="handleCreate">创建用户</NButton>
    </template>
    <NDataTable
      :columns="columns"
      :data="store.userList"
      :loading="store.loading"
      :pagination="{
        page: store.pagination.page,
        pageSize: store.pagination.pageSize,
        itemCount: store.pagination.total,
        'onUpdate:page': (page: number) => { store.pagination.page = page; fetchData() },
        'onUpdate:pageSize': (size: number) => { store.pagination.pageSize = size; store.pagination.page = 1; fetchData() },
      }"
      :empty="() => h(NEmpty, { description: '暂无用户' })"
    />
    <UserFormModal
      :show="showModal"
      :user="editingUser"
      @update:show="showModal = $event"
      @saved="handleSaved"
    />
  </NCard>
</template>
