import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'

vi.mock('@/utils/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('user store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fetchUsers sends GET /users and sets userList / pagination', async () => {
    const request = (await import('@/utils/request')).default
    const mockUsers = [
      { id: 1, username: 'admin', displayName: '管理员', role: 'ADMIN', status: 'ENABLED', createdAt: '2025-01-01T00:00:00', lastModifiedAt: '2025-01-01T00:00:00', createdBy: 'system', lastModifiedBy: 'system' },
      { id: 2, username: 'user1', displayName: '用户1', role: 'USER', status: 'ENABLED', createdAt: '2025-01-02T00:00:00', lastModifiedAt: '2025-01-02T00:00:00' },
    ]
    request.get.mockResolvedValue({ data: { data: mockUsers, total: 2 } })

    const store = useUserStore()
    await store.fetchUsers()

    expect(request.get).toHaveBeenCalledWith('/users', {
      params: { pageNum: 1, pageSize: 20 },
    })
    expect(store.userList).toEqual(mockUsers)
    expect(store.pagination.total).toBe(2)
    expect(store.loading).toBe(false)
  })

  it('fetchUsers handles null data with empty array default', async () => {
    const request = (await import('@/utils/request')).default
    request.get.mockResolvedValue({ data: { data: null, total: 0 } })

    const store = useUserStore()
    await store.fetchUsers()

    expect(store.userList).toEqual([])
    expect(store.pagination.total).toBe(0)
  })

  it('fetchUsers updates page state from params', async () => {
    const request = (await import('@/utils/request')).default
    request.get.mockResolvedValue({ data: { data: [], total: 0 } })

    const store = useUserStore()
    await store.fetchUsers({ page: 2, pageSize: 10 })

    expect(request.get).toHaveBeenCalledWith('/users', {
      params: { pageNum: 2, pageSize: 10 },
    })
    expect(store.pagination.page).toBe(2)
    expect(store.pagination.pageSize).toBe(10)
  })

  it('createUser sends POST /users with correct data', async () => {
    const request = (await import('@/utils/request')).default
    const payload = {
      username: 'newuser',
      displayName: '新用户',
      password: 'password123',
      role: 'USER',
    }
    const responseData = { code: 200, data: { id: 3, ...payload } }
    request.post.mockResolvedValue({ data: responseData })

    const store = useUserStore()
    const result = await store.createUser(payload)

    expect(request.post).toHaveBeenCalledWith('/users', payload)
    expect(result).toEqual(responseData)
  })

  it('updateUser sends PUT /users/:id with correct data', async () => {
    const request = (await import('@/utils/request')).default
    const payload = {
      displayName: 'Updated',
      role: 'ADMIN',
    }
    const responseData = { code: 200, data: { id: 1, ...payload } }
    request.put.mockResolvedValue({ data: responseData })

    const store = useUserStore()
    const result = await store.updateUser(1, payload)

    expect(request.put).toHaveBeenCalledWith('/users/1', payload)
    expect(result).toEqual(responseData)
  })

  it('deleteUser sends DELETE /users/:id', async () => {
    const request = (await import('@/utils/request')).default
    request.delete.mockResolvedValue({})

    const store = useUserStore()
    await store.deleteUser(1)

    expect(request.delete).toHaveBeenCalledWith('/users/1')
  })
})
