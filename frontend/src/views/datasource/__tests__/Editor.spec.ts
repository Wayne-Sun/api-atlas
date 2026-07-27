import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h } from 'vue'
import Editor from '../Editor.vue'

// Hoisted mocks referenced inside vi.mock factories
const { mockValidate, mockMessage, mockPush, mockRouteParams, mockStore } = vi.hoisted(() => ({
  mockValidate: vi.fn(),
  mockMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
  },
  mockPush: vi.fn(),
  mockRouteParams: {} as Record<string, string>,
  mockStore: {
    create: vi.fn(),
    update: vi.fn(),
    getById: vi.fn(),
    testConnection: vi.fn(),
    current: null as any,
  },
}))

vi.mock('naive-ui', () => ({
  NForm: { name: 'NForm' },
  NFormItem: { name: 'NFormItem' },
  NInput: { name: 'NInput' },
  NInputNumber: { name: 'NInputNumber' },
  NSelect: { name: 'NSelect' },
  NButton: { name: 'NButton' },
  NSpace: { name: 'NSpace' },
  NCard: { name: 'NCard' },
  useMessage: () => mockMessage,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ params: mockRouteParams }),
}))

vi.mock('@/stores/datasource', () => ({
  useDatasourceStore: () => mockStore,
}))

// Component stubs with render functions for slot rendering and NForm expose
const stubs = {
  NForm: {
    setup(_: any, { expose, slots }: any) {
      expose({ validate: mockValidate })
      return () => h('form', null, slots.default?.())
    },
  },
  NFormItem: {
    setup(_: any, { slots }: any) {
      return () => h('div', null, slots.default?.())
    },
  },
  NInput: {
    setup() {
      return () => h('input')
    },
  },
  NInputNumber: {
    setup() {
      return () => h('input', { type: 'number' })
    },
  },
  NSelect: {
    setup() {
      return () => h('select')
    },
  },
  NButton: {
    setup(_: any, { slots }: any) {
      return () => h('button', null, slots.default?.())
    },
  },
  NSpace: {
    setup(_: any, { slots }: any) {
      return () => h('div', null, slots.default?.())
    },
  },
  NCard: {
    setup(_: any, { slots }: any) {
      return () => h('div', { class: 'mock-card' }, slots.default?.())
    },
  },
}

function createWrapper(routeParams: Record<string, string> = {}) {
  Object.assign(mockRouteParams, routeParams)
  setActivePinia(createPinia())
  return mount(Editor, {
    global: { stubs },
  })
}

describe('Editor.vue', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    for (const key of Object.keys(mockRouteParams)) {
      delete mockRouteParams[key]
    }
    mockStore.current = null
  })

  it('mounts successfully in create mode', () => {
    const wrapper = createWrapper()
    expect(wrapper.exists()).toBe(true)
    wrapper.unmount()
  })

  it('renders form with all expected fields for MySQL type', () => {
    const wrapper = createWrapper()

    // 3 buttons: test connection, save, cancel
    const buttons = wrapper.findAll('button')
    expect(buttons.length).toBe(3)
    expect(buttons[0].text()).toBe('测试连接')
    expect(buttons[1].text()).toBe('保存')
    expect(buttons[2].text()).toBe('取消')

    // 5 text inputs (name, host, databaseName, username, password)
    const textInputs = wrapper.findAll('input:not([type="number"])')
    expect(textInputs.length).toBe(5)

    // 1 number input (port)
    const numberInputs = wrapper.findAll('input[type="number"]')
    expect(numberInputs.length).toBe(1)

    // 1 select (type)
    const selects = wrapper.findAll('select')
    expect(selects.length).toBe(1)

    wrapper.unmount()
  })

  it('blocks save when form validation fails', async () => {
    mockValidate.mockRejectedValue(new Error('validation failed'))
    const wrapper = createWrapper()

    const saveButton = wrapper.findAll('button').find((b) => b.text() === '保存')!
    await saveButton.trigger('click')
    await flushPromises()

    expect(mockStore.create).not.toHaveBeenCalled()
    expect(mockStore.update).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('test connection button is present and triggers store.testConnection', async () => {
    mockStore.testConnection.mockResolvedValue({
      data: { connected: true, responseTime: 120 },
    })
    const wrapper = createWrapper()

    const testButton = wrapper.findAll('button').find((b) => b.text() === '测试连接')!
    expect(testButton.exists()).toBe(true)

    await testButton.trigger('click')
    await flushPromises()

    expect(mockStore.testConnection).toHaveBeenCalledTimes(1)
    expect(mockMessage.success).toHaveBeenCalledWith('连接成功 (120ms)')
    wrapper.unmount()
  })

  it('cancel button navigates back to /datasource', async () => {
    const wrapper = createWrapper()

    const cancelButton = wrapper.findAll('button').find((b) => b.text() === '取消')!
    await cancelButton.trigger('click')

    expect(mockPush).toHaveBeenCalledWith('/datasource')
    wrapper.unmount()
  })
})
