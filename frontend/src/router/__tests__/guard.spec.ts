import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import router from '@/router/index.ts'

const mockAuthStore = vi.hoisted(() => ({
  token: null as string | null,
  currentUser: null as { id: number; username: string; displayName: string; role: string } | null,
  isAuthenticated: false,
  isAdmin: false,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

describe('router guard', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    mockAuthStore.token = null
    mockAuthStore.currentUser = null
    mockAuthStore.isAuthenticated = false
    mockAuthStore.isAdmin = false
    // Reset to a known state
    await router.push('/login').catch(() => {})
  })

  it('unauthenticated_redirectsToLogin', async () => {
    mockAuthStore.isAuthenticated = false
    mockAuthStore.isAdmin = false

    await router.push('/datasource')

    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('nonAdmin_redirectsFromUserPage', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = false
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }

    await router.push('/user')

    expect(router.currentRoute.value.name).toBe('DatasourceList')
  })

  it('admin_accessesUserPage', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }

    await router.push('/user')

    expect(router.currentRoute.value.name).toBe('UserList')
  })
})
