import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { h } from 'vue'
import Index from '../index.vue'

const { mockStore, mockPush } = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockStore: {
    fetchList: vi.fn(),
    list: [],
    total: 0,
    loading: false,
    toggleStatus: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('naive-ui', () => ({
  NButton: { name: 'NButton' },
  NDataTable: { name: 'NDataTable' },
  NTag: { name: 'NTag' },
  NSwitch: { name: 'NSwitch' },
  NSpace: { name: 'NSpace' },
  NInput: { name: 'NInput' },
  NSelect: { name: 'NSelect' },
  NEmpty: { name: 'NEmpty' },
  NCard: { name: 'NCard' },
  NPopconfirm: { name: 'NPopconfirm' },
  useMessage: () => ({ error: vi.fn() }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

vi.mock('@/stores/datasource', () => ({
  useDatasourceStore: () => mockStore,
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
    setup(_: any, { slots }: any) {
      return () => h('div', null, slots.default?.())
    },
  },
  NSpace: {
    setup(_: any, { slots }: any) {
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

describe('index.vue', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mockStore.list = []
    mockStore.total = 0
  })

  it('filter type dropdown options include MongoDB', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    const select = wrapper.findComponent({ name: 'NSelect' })
    const options = select.props('options') as { label: string; value: string }[]
    expect(options).toEqual(
      expect.arrayContaining([{ label: 'MongoDB', value: 'MongoDB' }])
    )
    wrapper.unmount()
  })

  it('fetches datasource list on mount', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(mockStore.fetchList).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
})
