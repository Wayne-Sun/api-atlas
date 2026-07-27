import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useDatasourceStore } from '@/stores/datasource'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}))

describe('datasource store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList sends GET /datasources and sets list / total', async () => {
    const request = (await import('@/utils/request')).default
    const mockData = [
      { id: 1, name: 'ds1', type: 'mysql', host: 'localhost', port: 3306, status: 'active', createdAt: '2025-01-01', updatedAt: '2025-01-01' },
    ]
    request.get.mockResolvedValue({ data: { data: mockData, total: 1 } })

    const store = useDatasourceStore()
    await store.fetchList()

    expect(request.get).toHaveBeenCalledWith('/datasources', { params: undefined })
    expect(store.list).toEqual(mockData)
    expect(store.total).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('fetchList sends query params when provided', async () => {
    const request = (await import('@/utils/request')).default
    request.get.mockResolvedValue({ data: { data: [], total: 0 } })

    const store = useDatasourceStore()
    await store.fetchList({ name: 'test', type: 'mysql', status: 'active', pageNum: 1, pageSize: 20 })

    expect(request.get).toHaveBeenCalledWith('/datasources', {
      params: { name: 'test', type: 'mysql', status: 'active', pageNum: 1, pageSize: 20 },
    })
  })

  it('fetchList handles null/undefined data with empty array default', async () => {
    const request = (await import('@/utils/request')).default
    request.get.mockResolvedValue({ data: { data: null, total: 0 } })

    const store = useDatasourceStore()
    await store.fetchList()

    expect(store.list).toEqual([])
    expect(store.total).toBe(0)
  })

  it('create sends POST /datasources and returns response data', async () => {
    const request = (await import('@/utils/request')).default
    const payload = { name: 'new-ds', type: 'mysql', host: 'db.example.com', port: 3306, status: 'active' }
    const responseData = { code: 200, data: { id: 42, ...payload } }
    request.post.mockResolvedValue({ data: responseData })

    const store = useDatasourceStore()
    const result = await store.create(payload)

    expect(request.post).toHaveBeenCalledWith('/datasources', payload)
    expect(result).toEqual(responseData)
  })

  it('update sends PUT /datasources/:id and returns response data', async () => {
    const request = (await import('@/utils/request')).default
    const payload = { name: 'updated' }
    const responseData = { code: 200, data: { id: 1, name: 'updated' } }
    request.put.mockResolvedValue({ data: responseData })

    const store = useDatasourceStore()
    const result = await store.update(1, payload)

    expect(request.put).toHaveBeenCalledWith('/datasources/1', payload)
    expect(result).toEqual(responseData)
  })

  it('remove sends DELETE /datasources/:id', async () => {
    const request = (await import('@/utils/request')).default
    request.delete.mockResolvedValue({})

    const store = useDatasourceStore()
    await store.remove(1)

    expect(request.delete).toHaveBeenCalledWith('/datasources/1')
  })

  it('getById sends GET /datasources/:id and sets current', async () => {
    const request = (await import('@/utils/request')).default
    const mockCurrent = { id: 7, name: 'my-ds', type: 'postgresql', host: 'pg.example.com', port: 5432, status: 'active', createdAt: '2025-01-01', updatedAt: '2025-01-01' }
    request.get.mockResolvedValue({ data: { data: mockCurrent } })

    const store = useDatasourceStore()
    await store.getById(7)

    expect(request.get).toHaveBeenCalledWith('/datasources/7')
    expect(store.current).toEqual(mockCurrent)
  })

  it('toggleStatus sends PATCH /datasources/:id/status', async () => {
    const request = (await import('@/utils/request')).default
    request.patch.mockResolvedValue({})

    const store = useDatasourceStore()
    await store.toggleStatus(1, 'inactive')

    expect(request.patch).toHaveBeenCalledWith('/datasources/1/status', { status: 'inactive' })
  })

  it('testConnection sends POST /datasources/test-connection and returns response', async () => {
    const request = (await import('@/utils/request')).default
    const connData = { type: 'mysql', host: 'localhost', port: 3306, databaseName: 'test', username: 'root', password: 'root' }
    const responseData = { code: 200, data: { success: true }, message: 'ok' }
    request.post.mockResolvedValue({ data: responseData })

    const store = useDatasourceStore()
    const result = await store.testConnection(connData)

    expect(request.post).toHaveBeenCalledWith('/datasources/test-connection', connData)
    expect(result).toEqual(responseData)
  })
})
