import { describe, it, expect, vi, beforeAll } from 'vitest'

// hoisted before vi.mock so interceptors ref is available in mock factory
const interceptors = vi.hoisted(() => ({
  onFulfilled: null as any,
  onRejected: null as any,
}))

vi.mock('naive-ui', () => ({
  createDiscreteApi: () => ({
    message: { error: vi.fn() },
  }),
}))

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => ({
      interceptors: {
        request: { use: vi.fn() },
        response: {
          use: vi.fn((f: any, r: any) => {
            interceptors.onFulfilled = f
            interceptors.onRejected = r
          }),
        },
      },
    })),
  },
}))

describe('request util', () => {
  // Trigger module init once — subsequent imports use cache
  beforeAll(async () => {
    await import('@/utils/request')
  })

  it('creates Axios instance with correct baseURL and timeout', async () => {
    const { default: axios } = await import('axios')
    expect(axios.create).toHaveBeenCalledWith({
      baseURL: '/api',
      timeout: 30000,
    })
  })

  it('registers response interceptor with fulfilled and rejected handlers', () => {
    expect(interceptors.onFulfilled).toBeInstanceOf(Function)
    expect(interceptors.onRejected).toBeInstanceOf(Function)
  })

  it('passes through successful response when code < 400', () => {
    const response = {
      data: { code: 200, data: { id: 1 }, message: 'ok' },
    }
    const result = interceptors.onFulfilled(response)
    expect(result).toBe(response)
  })

  it('rejects promise when response code >= 400', async () => {
    const response = {
      data: { code: 400, data: null, message: 'Bad request' },
    }
    await expect(interceptors.onFulfilled(response)).rejects.toThrow(
      'Bad request',
    )
  })

  it('rejects promise on network error', async () => {
    const error = new Error('Network Error')
    await expect(interceptors.onRejected(error)).rejects.toThrow(
      'Network Error',
    )
  })

  it('rejects promise with HTTP error containing response message', async () => {
    const error = {
      response: { data: { message: 'Server error' } },
      message: 'Internal',
    }
    await expect(interceptors.onRejected(error)).rejects.toEqual(error)
  })
})
