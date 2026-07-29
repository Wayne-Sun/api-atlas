import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import request from '@/utils/request'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  role: string
  status: string          // "ENABLED" | "DISABLED"
  createdBy?: string
  createdAt: string       // ISO date string from backend
  lastModifiedBy?: string
  lastModifiedAt?: string // ISO date string
}

export interface UserCreateDTO {
  username: string
  displayName: string
  password: string
  role: string
}

export interface UserUpdateDTO {
  displayName: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const userList = ref<UserInfo[]>([])
  const loading = ref(false)
  const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

  async function fetchUsers(params?: { page?: number; pageSize?: number }) {
    loading.value = true
    try {
      const res = await request.get('/users', {
        params: {
          pageNum: params?.page ?? pagination.page,
          pageSize: params?.pageSize ?? pagination.pageSize,
        },
      })
      userList.value = res.data.data || []
      pagination.total = res.data.total || 0
      if (params?.page !== undefined) pagination.page = params.page
      if (params?.pageSize !== undefined) pagination.pageSize = params.pageSize
    } finally {
      loading.value = false
    }
  }

  async function createUser(data: UserCreateDTO) {
    const res = await request.post('/users', data)
    return res.data
  }

  async function updateUser(id: number, data: UserUpdateDTO) {
    const res = await request.put(`/users/${id}`, data)
    return res.data
  }

  async function deleteUser(id: number) {
    await request.delete(`/users/${id}`)
  }

  return {
    userList,
    loading,
    pagination,
    fetchUsers,
    createUser,
    updateUser,
    deleteUser,
  }
})
