import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/utils/request', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('login stores token and user', async () => {
    const request = (await import('@/utils/request')).default
    const mockResponse = {
      data: {
        data: {
          accessToken: 'test-token',
          user: { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' },
        },
      },
    }
    request.post.mockResolvedValue(mockResponse)

    const authStore = useAuthStore()
    await authStore.login('admin', 'pass')

    expect(authStore.token).toBe('test-token')
    expect(authStore.currentUser?.username).toBe('admin')
    expect(localStorage.getItem('token')).toBe('test-token')
  })

  it('logout clears store', async () => {
    const request = (await import('@/utils/request')).default
    request.post.mockResolvedValue({})

    const authStore = useAuthStore()
    authStore.token = 'test-token'
    authStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }
    localStorage.setItem('token', 'test-token')

    await authStore.logout()

    expect(authStore.token).toBeNull()
    expect(authStore.currentUser).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('isAuthenticated computed returns true when token is set', () => {
    const authStore = useAuthStore()
    expect(authStore.isAuthenticated).toBe(false)

    authStore.token = 'some-token'
    expect(authStore.isAuthenticated).toBe(true)
  })

  it('isAdmin computed returns true for ADMIN role', () => {
    const authStore = useAuthStore()
    expect(authStore.isAdmin).toBe(false)

    authStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }
    expect(authStore.isAdmin).toBe(true)

    authStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }
    expect(authStore.isAdmin).toBe(false)
  })
})
