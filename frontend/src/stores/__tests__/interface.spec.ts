import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useInterfaceStore } from '@/stores/interface'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    patch: vi.fn(),
  },
}))

describe('interface store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchList sends GET /interfaces and sets list / total', async () => {
    const request = (await import('@/utils/request')).default
    const mockData = [
      {
        id: 1, englishName: 'getUser', chineseName: '获取用户', urlSlug: 'get-user',
        method: 'GET', dataSourceId: 1, dataSourceName: 'ds1',
        queryType: 'SQL', queryContent: 'SELECT * FROM users',
        isPaginated: false, pageSize: 20,
        status: 'active', createdAt: '2025-01-01', updatedAt: '2025-01-01',
      },
    ]
    request.get.mockResolvedValue({ data: { data: mockData, total: 1 } })

    const store = useInterfaceStore()
    await store.fetchList()

    expect(request.get).toHaveBeenCalledWith('/interfaces', { params: undefined })
    expect(store.list).toEqual(mockData)
    expect(store.total).toBe(1)
    expect(store.loading).toBe(false)
  })

  it('fetchList sends query params when provided', async () => {
    const request = (await import('@/utils/request')).default
    request.get.mockResolvedValue({ data: { data: [], total: 0 } })

    const store = useInterfaceStore()
    await store.fetchList({ dataSourceId: 2, name: 'user', status: 'active', pageNum: 1, pageSize: 10 })

    expect(request.get).toHaveBeenCalledWith('/interfaces', {
      params: { dataSourceId: 2, name: 'user', status: 'active', pageNum: 1, pageSize: 10 },
    })
  })

  it('fetchList handles null/undefined data with empty array default', async () => {
    const request = (await import('@/utils/request')).default
    request.get.mockResolvedValue({ data: { data: null, total: 0 } })

    const store = useInterfaceStore()
    await store.fetchList()

    expect(store.list).toEqual([])
    expect(store.total).toBe(0)
  })

  it('create sends POST /interfaces and returns response data', async () => {
    const request = (await import('@/utils/request')).default
    const payload = {
      englishName: 'createUser', chineseName: '创建用户', urlSlug: 'create-user',
      method: 'POST', dataSourceId: 1, queryType: 'SQL', queryContent: 'INSERT ...',
      isPaginated: false, pageSize: 20, status: 'active',
    }
    const responseData = { code: 200, data: { id: 10, ...payload } }
    request.post.mockResolvedValue({ data: responseData })

    const store = useInterfaceStore()
    const result = await store.create(payload)

    expect(request.post).toHaveBeenCalledWith('/interfaces', payload)
    expect(result).toEqual(responseData)
  })

  it('update sends PUT /interfaces/:id and returns response data', async () => {
    const request = (await import('@/utils/request')).default
    const payload = { englishName: 'renamed' }
    const responseData = { code: 200, data: { id: 5, englishName: 'renamed' } }
    request.put.mockResolvedValue({ data: responseData })

    const store = useInterfaceStore()
    const result = await store.update(5, payload)

    expect(request.put).toHaveBeenCalledWith('/interfaces/5', payload)
    expect(result).toEqual(responseData)
  })

  it('getById sends GET /interfaces/:id, sets current, and returns response', async () => {
    const request = (await import('@/utils/request')).default
    const mockCurrent = {
      id: 3, englishName: 'getById', chineseName: '根据ID获取', urlSlug: 'get-by-id',
      method: 'GET', dataSourceId: 1, dataSourceName: 'ds1',
      queryType: 'SQL', queryContent: 'SELECT * FROM t WHERE id = ?',
      isPaginated: false, pageSize: 20,
      status: 'active', createdAt: '2025-01-01', updatedAt: '2025-01-01',
    }
    const responseData = { code: 200, data: mockCurrent }
    request.get.mockResolvedValue({ data: responseData })

    const store = useInterfaceStore()
    const result = await store.getById(3)

    expect(request.get).toHaveBeenCalledWith('/interfaces/3')
    expect(store.current).toEqual(mockCurrent)
    expect(result).toEqual(responseData)
  })

  it('remove sends DELETE /interfaces/:id', async () => {
    const request = (await import('@/utils/request')).default
    request.delete.mockResolvedValue({})

    const store = useInterfaceStore()
    await store.remove(99)

    expect(request.delete).toHaveBeenCalledWith('/interfaces/99')
  })

  it('test sends POST /interfaces/:id/test and sets testResult', async () => {
    const request = (await import('@/utils/request')).default
    const testData = { rows: [{ id: 1, name: 'test' }] }
    const responseData = { code: 200, data: testData }
    request.post.mockResolvedValue({ data: responseData })

    const store = useInterfaceStore()
    const result = await store.test(1, { userId: '123' }, 1, 10)

    expect(request.post).toHaveBeenCalledWith('/interfaces/1/test', {
      params: { userId: '123' },
      pageNum: 1,
      pageSize: 10,
    })
    expect(store.testResult).toEqual(testData)
    expect(result).toEqual(responseData)
  })

  it('test sends POST with only id when no optional params', async () => {
    const request = (await import('@/utils/request')).default
    const responseData = { code: 200, data: null }
    request.post.mockResolvedValue({ data: responseData })

    const store = useInterfaceStore()
    await store.test(1)

    expect(request.post).toHaveBeenCalledWith('/interfaces/1/test', {
      params: undefined,
      pageNum: undefined,
      pageSize: undefined,
    })
  })

  it('updateStatus sends PATCH /interfaces/:id/status', async () => {
    const request = (await import('@/utils/request')).default
    request.patch.mockResolvedValue({})

    const store = useInterfaceStore()
    await store.updateStatus(1, 'inactive')

    expect(request.patch).toHaveBeenCalledWith('/interfaces/1/status', { status: 'inactive' })
  })
})
