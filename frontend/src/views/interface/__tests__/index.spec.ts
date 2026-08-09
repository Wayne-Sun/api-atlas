import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h } from 'vue'
import Index from '../index.vue'

const { mockStore, mockPush, mockAuthStore } = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockStore: {
    fetchList: vi.fn(),
    list: [],
    total: 0,
    loading: false,
    remove: vi.fn(),
    updateStatus: vi.fn(),
  },
  mockAuthStore: {
    isAdmin: false,
  },
}))

vi.mock('naive-ui', () => ({
  NButton: {
    name: 'NButton',
    props: ['type', 'size', 'loading', 'onClick'],
    setup(_: unknown, { slots }: { slots: Record<string, unknown> }) {
      return () => h('button', null, slots.default?.())
    },
  },
  NDataTable: {
    name: 'NDataTable',
    props: ['columns', 'data', 'loading', 'pagination', 'empty'],
    setup(props: { columns: unknown[] }) {
      return () => h('div', { 'data-columns': JSON.stringify(props.columns?.map((c: Record<string, unknown>) => c.key)) })
    },
  },
  NTag: { name: 'NTag' },
  NSpace: { name: 'NSpace' },
  NInput: { name: 'NInput' },
  NSelect: { name: 'NSelect' },
  NEmpty: { name: 'NEmpty' },
  NCard: { name: 'NCard' },
  NPopconfirm: { name: 'NPopconfirm' },
  useMessage: () => ({ success: vi.fn(), error: vi.fn() }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('@/stores/interface', () => ({
  useInterfaceStore: () => mockStore,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockAuthStore,
}))

const stubs = {
  NSelect: {
    name: 'NSelect',
    props: ['options', 'value'],
    setup() {
      return () => h('select')
    },
  },
  NCard: {
    setup(_: unknown, { slots }: { slots: Record<string, (() => unknown) | undefined> }) {
      return () => h('div', null, [
        slots['header-extra']?.(),
        slots.default?.()
      ])
    },
  },
  NSpace: {
    setup(_: unknown, { slots }: { slots: Record<string, (() => unknown) | undefined> }) {
      return () => h('div', null, slots.default?.())
    },
  },
  NInput: {
    setup() {
      return () => h('input')
    },
  },
}

function createWrapper() {
  setActivePinia(createPinia())
  return mount(Index, { global: { stubs } })
}

describe('interface/index.vue', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockStore.list = []
    mockStore.total = 0
  })

  it('fetches interface list on mount', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(mockStore.fetchList).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('admin columns include actions column', async () => {
    mockAuthStore.isAdmin = true
    const wrapper = createWrapper()
    await flushPromises()

    const dataTable = wrapper.findComponent({ name: 'NDataTable' })
    const columnKeys = JSON.parse(dataTable.attributes('data-columns') || '[]') as string[]
    expect(columnKeys).toContain('actions')
    mockAuthStore.isAdmin = false
    wrapper.unmount()
  })

  it('nonAdmin columns do not include actions column', async () => {
    mockAuthStore.isAdmin = false
    const wrapper = createWrapper()
    await flushPromises()

    const dataTable = wrapper.findComponent({ name: 'NDataTable' })
    const columnKeys = JSON.parse(dataTable.attributes('data-columns') || '[]') as string[]
    expect(columnKeys).not.toContain('actions')
    wrapper.unmount()
  })

  it('admin sees create button', async () => {
    mockAuthStore.isAdmin = true
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).toContain('新增接口')
    mockAuthStore.isAdmin = false
    wrapper.unmount()
  })

  it('nonAdmin does not see create button', async () => {
    mockAuthStore.isAdmin = false
    const wrapper = createWrapper()
    await flushPromises()

    expect(wrapper.text()).not.toContain('新增接口')
    wrapper.unmount()
  })
})
