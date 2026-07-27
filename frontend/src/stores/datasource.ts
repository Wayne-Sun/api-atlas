import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export interface DataSource {
  id: number
  name: string
  type: string
  host: string
  port: number
  databaseName?: string
  username?: string
  password?: string
  apiKey?: string
  status: string
  createdAt: string
  updatedAt: string
}

export const useDatasourceStore = defineStore('datasource', () => {
  const list = ref<DataSource[]>([])
  const loading = ref(false)
  const current = ref<DataSource | null>(null)
  const total = ref(0)

  async function fetchList(params?: { name?: string; type?: string; status?: string; pageNum?: number; pageSize?: number }) {
    loading.value = true
    try {
      const res = await request.get('/datasources', { params })
      list.value = res.data.data || []
      total.value = res.data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function create(data: Partial<DataSource>) {
    const res = await request.post('/datasources', data)
    return res.data
  }

  async function update(id: number, data: Partial<DataSource>) {
    const res = await request.put(`/datasources/${id}`, data)
    return res.data
  }

  async function remove(id: number) {
    await request.delete(`/datasources/${id}`)
  }

  async function getById(id: number) {
    const res = await request.get('/datasources/' + id)
    current.value = res.data.data
  }

  async function toggleStatus(id: number, status: string) {
    await request.patch(`/datasources/${id}/status`, { status })
  }

  async function testConnection(data: { type: string; host: string; port: number; databaseName?: string; username?: string; password?: string; apiKey?: string }) {
    const res = await request.post('/datasources/test-connection', data)
    return res.data
  }

  return { list, loading, current, total, fetchList, create, update, remove, getById, toggleStatus, testConnection }
})
