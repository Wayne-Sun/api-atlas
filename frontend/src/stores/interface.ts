import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export interface ApiInterface {
  id: number
  englishName: string
  chineseName: string
  urlSlug: string
  method: string
  dataSourceId: number
  dataSourceName: string
  queryType: string
  queryContent: string
  isPaginated: boolean
  pageSize: number
  status: string
  createdAt: string
  updatedAt: string
  params?: InterfaceParam[]
}

interface InterfaceParam {
  id: number
  paramName: string
  javaType: string
  remark: string
  sortOrder: number
}

export interface TestResult {
  rows: Record<string, unknown>[]
  total?: number
  pageNum?: number
  pageSize?: number
  responseTime?: number
}

export const useInterfaceStore = defineStore('interface', () => {
  const list = ref<ApiInterface[]>([])
  const loading = ref(false)
  const current = ref<ApiInterface | null>(null)
  const testResult = ref<TestResult | null>(null)
  const total = ref(0)

  async function fetchList(params?: { dataSourceId?: number; name?: string; status?: string; pageNum?: number; pageSize?: number }) {
    loading.value = true
    try {
      const res = await request.get('/interfaces', { params })
      list.value = res.data.data || []
      total.value = res.data.total || 0
    } finally {
      loading.value = false
    }
  }

  async function create(data: Partial<ApiInterface>) {
    const res = await request.post('/interfaces', data)
    return res.data
  }

  async function update(id: number, data: Partial<ApiInterface>) {
    const res = await request.put(`/interfaces/${id}`, data)
    return res.data
  }

  async function getById(id: number) {
    const res = await request.get(`/interfaces/${id}`)
    current.value = res.data.data
    return res.data
  }

  async function remove(id: number) {
    await request.delete(`/interfaces/${id}`)
  }

  async function test(id: number, params?: Record<string, unknown>, pageNum?: number, pageSize?: number) {
    const res = await request.post(`/interfaces/${id}/test`, { params, pageNum, pageSize })
    testResult.value = res.data.data
    return res.data
  }

  async function updateStatus(id: number, status: string) {
    await request.patch(`/interfaces/${id}/status`, { status })
  }

  return { list, loading, current, testResult, total, fetchList, create, update, getById, remove, test, updateStatus }
})
