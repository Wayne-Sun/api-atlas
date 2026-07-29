import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import request from '@/utils/request'

export interface UserInfo {
  id: number
  username: string
  displayName: string
  role: string
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const currentUser = ref<UserInfo | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

  async function login(username: string, password: string) {
    loading.value = true
    try {
      const res = await request.post('/auth/login', { username, password })
      const data = res.data.data
      token.value = data.accessToken
      currentUser.value = data.user
      localStorage.setItem('token', data.accessToken)
      return data
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      await request.post('/auth/logout')
    } finally {
      clearStore()
    }
  }

  async function fetchMe() {
    try {
      const res = await request.get('/auth/me')
      currentUser.value = res.data.data
    } catch {
      clearStore()
    }
  }

  function initFromStorage() {
    if (token.value && token.value !== 'null') {
      fetchMe()
    } else {
      clearStore()
    }
  }

  function clearStore() {
    token.value = null
    currentUser.value = null
    localStorage.removeItem('token')
  }

  return {
    token,
    currentUser,
    loading,
    isAuthenticated,
    isAdmin,
    login,
    logout,
    fetchMe,
    initFromStorage,
    clearStore,
  }
})
