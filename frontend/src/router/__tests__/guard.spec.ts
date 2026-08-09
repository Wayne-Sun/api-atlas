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

  it('nonAdmin_redirectsFromDatasourceCreate', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = false
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }

    await router.push('/datasource/create')

    expect(router.currentRoute.value.name).toBe('DatasourceList')
  })

  it('nonAdmin_redirectsFromDatasourceEdit', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = false
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }

    await router.push('/datasource/edit/1')

    expect(router.currentRoute.value.name).toBe('DatasourceList')
  })

  it('admin_accessesDatasourceCreate', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }

    await router.push('/datasource/create')

    expect(router.currentRoute.value.name).toBe('DatasourceCreate')
  })

  it('admin_accessesDatasourceEdit', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }

    await router.push('/datasource/edit/1')

    expect(router.currentRoute.value.name).toBe('DatasourceEdit')
  })

  it('nonAdmin_redirectsFromInterfaceCreate', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = false
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }

    await router.push('/interface/create')

    expect(router.currentRoute.value.name).toBe('DatasourceList')
  })

  it('nonAdmin_redirectsFromInterfaceEdit', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = false
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }

    await router.push('/interface/edit/1')

    expect(router.currentRoute.value.name).toBe('DatasourceList')
  })

  it('nonAdmin_redirectsFromInterfaceTest', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = false
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 2, username: 'user', displayName: 'User', role: 'USER' }

    await router.push('/interface/test/1')

    expect(router.currentRoute.value.name).toBe('DatasourceList')
  })

  it('admin_accessesInterfaceCreate', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }

    await router.push('/interface/create')

    expect(router.currentRoute.value.name).toBe('InterfaceCreate')
  })

  it('admin_accessesInterfaceEdit', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }

    await router.push('/interface/edit/1')

    expect(router.currentRoute.value.name).toBe('InterfaceEdit')
  })

  it('admin_accessesInterfaceTest', async () => {
    mockAuthStore.isAuthenticated = true
    mockAuthStore.isAdmin = true
    mockAuthStore.token = 'token'
    mockAuthStore.currentUser = { id: 1, username: 'admin', displayName: 'Admin', role: 'ADMIN' }

    await router.push('/interface/test/1')

    expect(router.currentRoute.value.name).toBe('InterfaceTest')
  })
})
