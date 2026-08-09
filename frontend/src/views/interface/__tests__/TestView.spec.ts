import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import TestView from '@/views/interface/TestView.vue'

const mockMessage = { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
const mockGetById = vi.fn().mockResolvedValue(undefined)
const mockTest = vi.fn().mockResolvedValue({ data: { rows: [], responseTime: 100 } })
const mockUpdateStatus = vi.fn().mockResolvedValue(undefined)

const mockAuthStore = vi.hoisted(() => ({
  isAdmin: false,
}))

vi.mock('naive-ui', () => ({
  NCard: { name: 'NCard', template: '<div><slot /></div>', props: ['title', 'size'] },
  NForm: { name: 'NForm', template: '<form><slot /></form>' },
  NFormItem: { name: 'NFormItem', template: '<div>{{ label }}<slot /></div>', props: ['label'] },
  NInput: { name: 'NInput', template: '<input />', props: ['value', 'placeholder'] },
  NInputNumber: { name: 'NInputNumber', template: '<input type="number" />', props: ['value', 'style'] },
  NButton: { name: 'NButton', template: '<button><slot /></button>', props: ['type', 'loading', 'onClick'] },
  NSpace: { name: 'NSpace', template: '<div><slot /></div>', props: ['size', 'vertical', 'style'] },
  NTag: { name: 'NTag', template: '<span><slot /></span>', props: ['type'] },
  useMessage: () => mockMessage,
}))

vi.mock('@/stores/interface', () => ({
  useInterfaceStore: vi.fn(() => ({
    current: {
      id: 1,
      englishName: 'test',
      chineseName: '测试接口',
      queryType: 'SQL',
      status: 'PENDING_TEST',
      isPaginated: false,
      params: [{ paramName: 'userId', javaType: 'String', remark: '用户ID' }],
    },
    getById: mockGetById,
    test: mockTest,
    updateStatus: mockUpdateStatus,
  })),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/interface', name: 'InterfaceList', component: { template: '<div />' } },
      { path: '/interface/test/:id', name: 'InterfaceTest', component: { template: '<div />' } },
      { path: '/interface/edit/:id', name: 'InterfaceEdit', component: { template: '<div />' } },
    ],
  })
}

async function mountTestView() {
  const pinia = createPinia()
  const router = makeRouter()
  await router.push('/interface/test/1')
  await router.isReady()

  const wrapper = mount(TestView, {
    global: {
      plugins: [pinia, router],
    },
  })
  await nextTick()
  await nextTick()
  return { wrapper, router }
}

describe('TestView.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAuthStore.isAdmin = false
  })

  it('admin sees test button', async () => {
    mockAuthStore.isAdmin = true
    const { wrapper } = await mountTestView()
    await flushPromises()

    const texts = wrapper.findAll('button').map(b => b.text())
    expect(texts).toContain('测试')
    mockAuthStore.isAdmin = false
    wrapper.unmount()
  })

  it('nonAdmin does not see test button', async () => {
    mockAuthStore.isAdmin = false
    const { wrapper } = await mountTestView()
    await flushPromises()

    const texts = wrapper.findAll('button').map(b => b.text())
    expect(texts).not.toContain('测试')
    wrapper.unmount()
  })

  it('admin sees online button when result is set', async () => {
    mockAuthStore.isAdmin = true
    const { wrapper } = await mountTestView()
    await flushPromises()

    const vm = wrapper.vm as unknown as { result: unknown[] }
    vm.result = [{ id: 1 }]
    await nextTick()

    const texts = wrapper.findAll('button').map(b => b.text())
    expect(texts).toContain('提交上线')
    mockAuthStore.isAdmin = false
    wrapper.unmount()
  })

  it('nonAdmin does not see online button', async () => {
    mockAuthStore.isAdmin = false
    const { wrapper } = await mountTestView()
    await flushPromises()

    const texts = wrapper.findAll('button').map(b => b.text())
    expect(texts).not.toContain('提交上线')
    wrapper.unmount()
  })
})
