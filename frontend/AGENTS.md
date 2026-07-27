# API Atlas — Frontend Knowledge Base

Vue 3 + Vite 8 + TypeScript 6 + Naive UI 2.44 + Pinia + Vue Router (hash mode).

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add page | `src/views/` | New .vue file, add route in `router/index.ts` |
| Add component | `src/components/` | Reusable UI components |
| Add store | `src/stores/` | Pinia composition API, export `useXxxStore()` |
| Add API call | `src/utils/request.ts` | Axios instance with interceptors |
| Global config | `vite.config.ts` | Aliases, plugins, proxy |
| Layout wrapper | `src/layouts/BaseLayout.vue` | Parent route with `<router-view>` |
| Type definitions | `src/types/` or inline in stores | TypeScript interfaces |

## CONVENTIONS

### Vue 3 + TypeScript
- ALWAYS use `<script setup lang="ts">` — no Options API.
- Define props with `defineProps<{...}>()` and emits with `defineEmits<[...]>`.
- Use `ref`/`reactive` for state, `computed` for derived state.
- Type all reactive variables explicitly: `const x = ref<string>('')` or `const x = ref<string | null>(null)`.

### Naive UI
- Components imported from `naive-ui` in each file (tree-shaking friendly).
- Global registration in `main.ts` for common components (NButton, NInput, NSelect, NDataTable, NCard, NTag, NSwitch, NSpace, NEmpty, NPopconfirm, NForm, NFormItem, NMessage).
- Use `h()` render functions for DataTable column renderers and dynamic components.
- Message/Notification: `message.success()`, `message.error()`, `notification.success()` from `naive-ui`.

### Pinia Stores
- Composition API style: `export const useXxxStore = defineStore('xxx', () => { ... })`.
- State: `ref` for primitives/arrays, `reactive` for objects.
- Actions: `async function` returning typed results.
- Getters: `computed` for derived state.
- Export everything in return object: `return { state, action, getter }`.

### Axios + Request Utility
- Single Axios instance in `src/utils/request.ts` with baseURL, timeout, interceptors.
- Request interceptor: adds auth headers if needed.
- Response interceptor: unwraps `R<T>` envelope — returns `response.data` (the `R` object), handles global error display via `message.error()`.
- Stores call `request.get/post/put/delete` and read `res.data.data` for payload, `res.data.total` for pagination total.
- NEVER access `res.data.data.data` — the interceptor already unwraps one level.

### Routing
- Hash mode (`createWebHashHistory`) — no server config needed.
- Parent routes with `BaseLayout` component: `/datasource` and `/interface` have children.
- `BaseLayout.vue` contains `<NLayout>` + `<NLayoutContent>` + `<router-view>`.
- Route definitions in `router/index.ts` with lazy imports: `() => import('@/views/...')`.

### Type Safety
- NO `as any` — use proper types or `unknown` with type guards.
- NO `@ts-ignore` — fix the type error instead.
- Define interfaces for API responses: `R<T>`, paginated list responses, entity types.
- Store state interfaces match backend DTOs/Entities.

### Error Handling
- Axios interceptor handles global errors (shows `message.error()`).
- Component catch blocks: add `console.warn('描述:', e)` before `// handled by interceptor` comment.
- Never leave empty catch blocks without at least a warning log.
- Typed errors: backend throws specific exceptions mapped to HTTP codes.

### Pagination
- Frontend stores use `res.data.total || 0` for total count.
- NEVER fall back to `res.data.pageSize` — the backend `R.ok(list, pageInfo)` sets `total` correctly.
- PageHelper on backend returns `PageInfo` with correct `total`.
- NDataTable pagination prop is a **plain JS object** — Vue template kebab-to-camel conversion does NOT apply. Always use `'onUpdate:pageSize': (size) => { ... }` (quoted), NOT `onUpdatePageSize`.

## TESTING SCOPE

新功能（Store / Util / Component / View / Router guard）完成后必须编写对应测试。

### Layer Test Rules

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Store** | 纯逻辑单元测试 | Vitest + `vi.mock('@/utils/request')` | `src/stores/__tests__/*.spec.ts` |
| **Util** | 纯逻辑单元测试 | Vitest | `src/utils/__tests__/*.spec.ts` |
| **Component (.vue)** | 组件挂载测试 | Vitest + `@vue/test-utils` | `src/components/__tests__/*.spec.ts` |
| **View** | 暂不要求 | — | — |
| **Router guard** | 纯逻辑单元测试 | Vitest + `vue-router` mock | `src/router/__tests__/*.spec.ts` |

### 测试配置

- 测试框架：Vitest v4.x（已配置于 `vitest.config.ts`）
- 测试环境：`jsdom`（DOM API 模拟）
- 路径别名：`@` → `src/`（与 `vite.config.ts` 一致）
- 运行命令：`npm run test:run`（CI 模式）或 `npm test`（watch 模式）

### Store 测试规范

```typescript
import { setActivePinia, createPinia } from 'pinia'
import { useXxxStore } from '@/stores/xxx'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn(), patch: vi.fn() },
}))

describe('xxx store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })
})
```

- 每个 `beforeEach` 中调用 `setActivePinia(createPinia())` 确保状态隔离
- 使用 `vi.mock('@/utils/request')` mock 所有 API 请求
- 每个 Store action 至少覆盖：成功返回数据、返回空数据、API 报错三种场景
- 验证请求参数（URL、method）和 Store 状态变更（list、total、current、loading）

### Util 测试规范

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'
```

- Axios 拦截器测试使用 `vi.hoisted()` 捕获 `interceptors.response.use()` 回调
- 覆盖正常响应（`code < 400`）、业务错误（`code >= 400`）、网络错误三种场景
- Util 函数测试覆盖正常输入、边界值、异常输入

### Component 测试规范（将来需补充时）

- 使用 `mount()` 或 `shallowMount()` 挂载组件
- 验证：组件渲染正确、props 响应、emit 事件触发
- Naive UI 组件需在测试中注册（`global.components` 或 `global.plugins`）
- 不测试真实 API 调用 — store 请求应提前 mock

### 方法命名规范

```
describe('store/xxx') → it('action name - scenario - expected')
```

示例：
- `it('fetchList - empty response - sets list to [] and total to 0')`
- `it('create - API error - throws error and does not update list')`

### 最低覆盖要求

- **Store**: 每个 action 至少一个 happy-path 测试
- **Util**: 每个导出函数至少 3 个场景（正常、边界、异常）
- **Component**: state/props/emit 各至少一个测试

### 验收标准

```bash
cd frontend && npm run test:run
# 应看到所有 Test Files passed，Tests 全部 Pass
```

## ANTI-PATTERNS

- Do NOT use `as any` or `@ts-ignore` — fix the type properly.
- Do NOT use Options API (`export default { data() {...} }`) — use `<script setup lang="ts">`.
- Do NOT access `res.data.data.data` — interceptor unwraps to `res.data` (the R envelope), then `.data` is the payload.
- Do NOT use `res.data.total || res.data.pageSize || 0` — always `res.data.total || 0`.
- Do NOT leave empty catch blocks — always `console.warn('Operation failed:', e)`.
- Do NOT hardcode API URLs in components — use the `request` utility.
- Do NOT put business logic in components — use stores.
- Do NOT mutate props — use `defineProps` with proper types.
- Do NOT use `v-model` on non-form elements without proper modifiers.
- Do NOT import Naive UI components globally in every file — import what you need (tree-shaking).

## COMMANDS

```bash
cd frontend
npm run dev           # Vite dev server (default :5173)
npm run build         # Type-check + production build
npm run type-check    # vue-tsc only (no build)
```

## NOTES

- TypeScript 6 with `node24` target.
- `@` alias maps to `src/` (configured in `vite.config.ts`).
- Naive UI 2.44 is a large library — production chunk ~1.3 MB raw. Code-split imports for real components (await import).
- No testing framework installed yet — Vitest is recommended when adding tests.