import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import Editor from '@/views/interface/Editor.vue'

// ── Mock references (accessible in tests) ────────────────────────
const mockValidate = vi.fn()
const mockMessage = { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }
const mockFetchDatasourceList = vi.fn().mockResolvedValue(undefined)
const mockFetchInterfaceList = vi.fn().mockResolvedValue(undefined)
const mockInterfaceCreate = vi.fn().mockResolvedValue({})
const mockInterfaceUpdate = vi.fn().mockResolvedValue({})
const mockInterfaceGetById = vi.fn().mockResolvedValue(undefined)

// ── Mock naive-ui ────────────────────────────────────────────────
vi.mock('naive-ui', () => ({
  NForm: {
    name: 'NForm',
    template: '<form><slot /></form>',
    props: ['model', 'rules', 'labelPlacement', 'labelWidth'],
    setup(_: unknown, { expose }: { expose: (obj: Record<string, unknown>) => void }) {
      expose({ validate: mockValidate })
    },
  },
  NFormItem: { name: 'NFormItem', template: '<div>{{ label }}<slot /></div>', props: ['label', 'path'] },
  NInput: { name: 'NInput', template: '<input />', props: ['value', 'type', 'rows', 'placeholder'] },
  NInputNumber: { name: 'NInputNumber', template: '<input type="number" />', props: ['value', 'style'] },
  NSelect: { name: 'NSelect', template: '<select />', props: ['value', 'options', 'placeholder'] },
  NButton: { name: 'NButton', template: '<button><slot /></button>', props: ['type', 'loading'] },
  NSwitch: { name: 'NSwitch', template: '<input type="checkbox" />', props: ['value'] },
  NSpace: { name: 'NSpace', template: '<div><slot /></div>', props: ['justify'] },
  NDataTable: { name: 'NDataTable', template: '<div />', props: ['columns', 'data', 'bordered', 'maxHeight'] },
  NCard: { name: 'NCard', template: '<div><h2>{{ title }}</h2><slot /></div>', props: ['title'] },
  useMessage: () => mockMessage,
}))

// ── Mock stores ──────────────────────────────────────────────────
vi.mock('@/stores/interface', () => ({
  useInterfaceStore: vi.fn(() => ({
    list: [],
    loading: false,
    current: null,
    testResult: null,
    total: 0,
    fetchList: mockFetchInterfaceList,
    create: mockInterfaceCreate,
    update: mockInterfaceUpdate,
    getById: mockInterfaceGetById,
    remove: vi.fn(),
    test: vi.fn(),
    updateStatus: vi.fn(),
  })),
}))

vi.mock('@/stores/datasource', () => ({
  useDatasourceStore: vi.fn(() => ({
    list: [
      { id: 1, name: 'MySQL Local', type: 'MySQL', host: 'localhost', port: 3306, status: 'active', createdAt: '', updatedAt: '' },
      { id: 2, name: 'ES Prod', type: 'Elasticsearch', host: 'es.local', port: 9200, status: 'active', createdAt: '', updatedAt: '' },
    ],
    loading: false,
    current: null,
    total: 2,
    fetchList: mockFetchDatasourceList,
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
    getById: vi.fn(),
    toggleStatus: vi.fn(),
    testConnection: vi.fn(),
  })),
}))

// ── Router + mount helper ────────────────────────────────────────
function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/interface', name: 'InterfaceList', component: { template: '<div />' } },
      { path: '/interface/create', name: 'InterfaceCreate', component: { template: '<div />' } },
      { path: '/interface/edit/:id', name: 'InterfaceEdit', component: { template: '<div />' } },
    ],
  })
}

async function mountEditor(initialRoute = '/interface/create') {
  const pinia = createPinia()
  const router = makeRouter()
  await router.push(initialRoute)
  await router.isReady()

  const wrapper = mount(Editor, {
    global: {
      plugins: [pinia, router],
    },
  })
  // Let onMounted resolve (datasourceStore.fetchList)
  await nextTick()
  await nextTick()
  return { wrapper, router }
}

// ── Tests ────────────────────────────────────────────────────────
describe('Editor.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchDatasourceList.mockResolvedValue(undefined)
    mockInterfaceGetById.mockResolvedValue(undefined)
    mockInterfaceCreate.mockResolvedValue({})
    mockInterfaceUpdate.mockResolvedValue({})
    mockValidate.mockReset()
  })

  it('mounts successfully in create mode', async () => {
    const { wrapper } = await mountEditor()
    expect(wrapper.exists()).toBe(true)
  })

  it('mounts successfully in edit mode', async () => {
    const { wrapper } = await mountEditor('/interface/edit/42')
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.find('h2').text()).toBe('编辑接口')
  })

  it('renders create-mode title by default', async () => {
    const { wrapper } = await mountEditor()
    expect(wrapper.find('h2').text()).toBe('新增接口')
  })

  it('renders all required form field labels', async () => {
    const { wrapper } = await mountEditor()
    const html = wrapper.html()
    expect(html).toContain('英文名称')
    expect(html).toContain('中文名称')
    expect(html).toContain('URL Slug')
    expect(html).toContain('请求方法')
    expect(html).toContain('数据源')
    expect(html).toContain('查询类型')
    expect(html).toContain('查询内容')
  })

  it('renders save and cancel buttons', async () => {
    const { wrapper } = await mountEditor()
    const buttons = wrapper.findAll('button')
    const texts = buttons.map(b => b.text())
    expect(texts).toContain('保存')
    expect(texts).toContain('取消')
  })

  it('calls validate on save click', async () => {
    mockValidate.mockRejectedValue(new Error('fail'))
    const { wrapper } = await mountEditor()

    const saveBtn = wrapper.findAll('button').find(b => b.text() === '保存')!
    await saveBtn.trigger('click')
    await nextTick()

    expect(mockValidate).toHaveBeenCalled()
  })

  it('cancel button navigates to /interface', async () => {
    const { wrapper, router } = await mountEditor()

    const cancelBtn = wrapper.findAll('button').find(b => b.text() === '取消')!
    await cancelBtn.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/interface')
  })

  it('extractParams extracts placeholders from queryContent', async () => {
    const { wrapper } = await mountEditor()
    const vm = wrapper.vm as unknown as {
      form: { queryContent: string }
      params: Array<{ paramName: string }>
    }

    vm.form.queryContent = 'SELECT ${userId}, ${userName} FROM users'
    await nextTick()
    await nextTick()

    const names = vm.params.map(p => p.paramName)
    expect(names).toContain('userId')
    expect(names).toContain('userName')
  })

  it('extractParams removes stale params', async () => {
    const { wrapper } = await mountEditor()
    const vm = wrapper.vm as unknown as {
      form: { queryContent: string }
      params: Array<{ paramName: string }>
    }

    vm.form.queryContent = 'SELECT ${a}, ${b} FROM t'
    await nextTick()
    await nextTick()
    expect(vm.params.map(p => p.paramName)).toEqual(expect.arrayContaining(['a', 'b']))

    vm.form.queryContent = 'SELECT ${a} FROM t'
    await nextTick()
    await nextTick()
    expect(vm.params.map(p => p.paramName)).toEqual(['a'])
  })

  it('saves via store.create in create mode on valid form', async () => {
    // Must resolve truthy: component checks `if (!valid) return` after .catch
    mockValidate.mockResolvedValue(true)
    const { wrapper } = await mountEditor()

    const saveBtn = wrapper.findAll('button').find(b => b.text() === '保存')!
    await saveBtn.trigger('click')
    await flushPromises()

    expect(mockInterfaceCreate).toHaveBeenCalled()
    expect(mockMessage.success).toHaveBeenCalledWith('创建成功')
  })

  it('shows placeholder text when params list is empty', async () => {
    const { wrapper } = await mountEditor()
    expect(wrapper.html()).toContain('自动生成参数')
  })
})
