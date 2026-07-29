import axios from 'axios'
import type { AxiosInstance, AxiosResponse } from 'axios'
import { createDiscreteApi } from 'naive-ui'

const { message: NMessage } = createDiscreteApi(['message'])

// Response envelope from backend
interface ApiResponse<T> {
  code: number
  data: T
  message: string
  total?: number
  pageNum?: number
  pageSize?: number
}

const request: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// Request interceptor: attach Bearer token from localStorage
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token && token !== 'null') {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor: unwrap data.data, handle errors
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data
    if (body.code && body.code >= 400) {
      NMessage.error(body.message || 'Request failed')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.hash = '#/login'
    }
    const msg = error.response?.data?.message || error.message || 'Network error'
    NMessage.error(msg)
    return Promise.reject(error)
  }
)

export default request
