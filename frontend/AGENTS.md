# API Atlas — Frontend Knowledge Base

Vue 3 + Vite 8 + TypeScript 6 + Naive UI 2.44 + Pinia + Vue Router (hash mode).

## WHERE TO LOOK

| Task | Location | Notes |
|------|----------|-------|
| Add page | `src/views/` | New .vue file, add route in `router/index.ts` |
| Add component | `src/views/*/components/` | View-scoped reusable components (e.g., `user/components/UserFormModal.vue`) |
| Add store | `src/stores/` | Pinia composition API, export `useXxxStore()` |
| Add auth store | `src/stores/auth.ts` | Token, currentUser, login/logout, isAuthenticated, isAdmin |
| Add user store | `src/stores/user.ts` | User CRUD (admin), UserInfo/UserCreateDTO/UserUpdateDTO types |
| Add API call | `src/utils/request.ts` | Axios instance with Bearer token + interceptors |
| Add Naive UI plugin | `src/utils/naive-ui.ts` | Tree-shaken `create()` component list |
| Global config | `vite.config.ts` | Aliases, plugins, proxy |
| Test config | `vitest.config.ts` | jsdom, @ alias, V8 coverage |
| Layout wrapper | `src/layouts/BaseLayout.vue` | Parent route with `<router-view>` |
| Login page | `src/views/login/index.vue` | Public route, no BaseLayout |
| User page | `src/views/user/index.vue` | Admin-only route |
| Interface test | `src/views/interface/TestView.vue` | Query test runner |
| NotFound | `src/views/NotFound.vue` | 404 catch-all `/:pathMatch(.*)*` |
| Route guard | `src/router/index.ts` | `beforeEach` — auth check + admin check |
| Type definitions | `src/stores/*.ts` or inline | TypeScript interfaces matching backend DTOs |

## CONVENTIONS

### Vue 3 + TypeScript
- ALWAYS use `<script setup lang="ts">` — no Options API.
- Define props with `defineProps<{...}>()` and emits with `defineEmits<[...]>`.
- Use `ref`/`reactive` for state, `computed` for derived state.
- Type all reactive variables explicitly: `const x = ref<string>('')` or `const x = ref<string | null>(null)`.

### Naive UI
- Naive UI 使用 **`create()` API 按需注册组件**（不再是全局 `app.use(naive)`），在 `src/utils/naive-ui.ts` 中维护组件列表。
- **`main.ts` 中只 `app.use(naiveUiPlugin)`**，不单独注册任何组件。
- 每个 .vue 文件仍需显式 `import { NButton } from 'naive-ui'`（tree-shaking 需要）。
- 使用 `h()` render 函数（而非 template）实现 DataTable 列渲染、自定义触发器。
- `NPopconfirm` 用于删除确认：`h(NPopconfirm, { onPositiveClick: () => handleDelete(row) }, { trigger: () => h(NButton, ...), default: () => '确定删除？' })`。
- `NDataTable` 的 `empty` prop 传 render function：`:empty="() => h(NEmpty, { description: '暂无数据' })"`（不可直接传 VNode）。
- `createDiscreteApi()` 在 `request.ts` 中独立使用，与 `create()` 兼容。
- 当前 `naive-ui.ts` 注册的组件列表：NButton, NCard, NConfigProvider, NDataTable, NEmpty, NForm, NFormItem, NIcon, NInput, NInputNumber, NLayout, NLayoutContent, NLayoutHeader, NLayoutSider, NMenu, NPopconfirm, NSelect, NSpace, NSwitch, NTag, NText。

### Pinia Stores
- Composition API style: `export const useXxxStore = defineStore('xxx', () => { ... })`.
- State: `ref` for primitives/arrays, `reactive` for objects.
- Actions: `async function` returning typed results.
- Getters: `computed` for derived state.
- Export everything in return object: `return { state, action, getter }`.
- Loading state bound to `NDataTable :loading`.

### Auth Store & Flow
- `auth.ts` store: `token` (localStorage), `currentUser`, `isAuthenticated` (computed from token), `isAdmin` (computed from role)。
- `login()`: POST /api/auth/login → 保存 `data.accessToken` 到 localStorage + store → 设置 `currentUser` → 返回 data。
- `logout()`: POST /api/auth/logout → `clearStore()` (清理 token + currentUser)。
- `fetchMe()`: GET /api/auth/me → 更新 currentUser (页面刷新后恢复 session)。
- `initFromStorage()`: 如果 token 存在且不为 null, 调用 `fetchMe()`; 否则 `clearStore()`。
- Token 持久化: localStorage `token` key; request interceptor 自动添加 `Authorization: Bearer {token}`。
- 401 响应: response interceptor 自动清理 token 并重定向到 /login。
- `UserInfo` 接口: `{ id, username, displayName, role }`。

### User Store
- `user.ts` store: `userList`, `pagination` (reactive `{ page, pageSize, total }`), `loading`.
- `fetchUsers()`: GET /api/users with pageNum/pageSize params.
- `createUser()`: POST /api/users with `UserCreateDTO`.
- `updateUser()`: PUT /api/users/:id with `UserUpdateDTO`.
- `deleteUser()`: DELETE /api/users/:id.
- 类型导出: `UserInfo`, `UserCreateDTO`, `UserUpdateDTO`。

### Axios + Request Utility
- Single Axios instance in `src/utils/request.ts` with `baseURL: '/api'`, `timeout: 30000`.
- Request interceptor: reads `localStorage.getItem('token')`, attaches `Authorization: Bearer {token}`.
- Response interceptor: 检查 `body.code >= 400` 时显示错误消息 (`NMessage.error()`) 并 reject; 401 时清理 token 并跳转登录。
- Stores call `request.get/post/put/delete` and read `res.data.data` for payload, `res.data.total || 0` for pagination total.
- NEVER access `res.data.data.data` — the interceptor already returns the R envelope.
- Component catch blocks: `console.warn('Operation failed:', e)` + comment `// handled by interceptor`.

### Routing
- Hash mode (`createWebHashHistory`) — no server config needed.
- Auth guard in `router.beforeEach`:
  - `/login` → public (no auth check).
  - `!isAuthenticated` → redirect to `/login`.
  - token exists but `currentUser` is null → call `fetchMe()` first.
  - `meta.requiresAdmin` + `!isAdmin` → redirect to `/datasource`.
- Parent-child routes: `/datasource`, `/interface`, `/user` are parent routes using `BaseLayout.vue`, with children for sub-pages.
- Route definitions with lazy imports: `component: () => import('@/views/...')`.
- 404 catch-all route `/:pathMatch(.*)*` → `NotFound.vue`.

### Type Safety
- NO `as any` — use proper types or `unknown` with type guards.
- NO `@ts-ignore` — fix the type error instead.
- Define interfaces for API responses, paginated list responses, entity types.
- Store state interfaces match backend DTOs/Entities.

### Error Handling
- Axios interceptor handles global errors (shows `message.error()`).
- Component catch blocks: add `console.warn('描述:', e)` before `// handled by interceptor` comment.
- Never leave empty catch blocks without at least a warning log.
- Typed errors: backend throws specific exceptions mapped to HTTP codes.
- Delete operations: `deletingId` ref pattern + `loading: deletingId === row.id` + `finally { deletingId = null }`.
- Global Vue errors: `app.config.errorHandler` in `main.ts`.

### Pagination
- Frontend stores use `res.data.total || 0` for total count.
- NEVER fall back to `res.data.pageSize` — the backend `R.ok(list, pageInfo)` sets `total` correctly.
- PageHelper on backend returns `PageInfo` with correct `total`.
- NDataTable pagination prop is a **plain JS object** — Vue template kebab-to-camel conversion does NOT apply:
  - ✅ `'onUpdate:pageSize': (size: number) => { ... }` (quoted)
  - ✅ `'onUpdate:page': (page: number) => { ... }` (quoted)
  - ❌ `onUpdatePageSize` / `onUpdate:pageSize` (unquoted) — will NOT work

## TESTING SCOPE

新功能（Store / Util / Component / View / Router guard）完成后必须编写对应测试。

### Layer Test Rules

| 层 | 测试类型 | 框架 | 位置 |
|----|---------|------|------|
| **Store** | 纯逻辑单元测试 | Vitest + `vi.mock('@/utils/request')` | `src/stores/__tests__/*.spec.ts` |
| **Util** | 纯逻辑单元测试 | Vitest | `src/utils/__tests__/*.spec.ts` |
| **Component (.vue)** | 组件挂载测试 | Vitest + `@vue/test-utils` | `src/views/*/__tests__/*.spec.ts` |
| **View** | 暂不要求 | — | — |
| **Router / Guard** | 纯逻辑单元测试 | Vitest + `vue-router` mock | `src/router/__tests__/*.spec.ts` |

### 测试配置

- 测试框架：Vitest v4.x（已配置于 `vitest.config.ts`）
- 测试环境：`jsdom`（DOM API 模拟）
- 覆盖率：V8 provider，`text` + `json` + `html` reporter
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

### Component 测试规范

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
- Do NOT import Naive UI globally in every file — import what each file needs (tree-shaking).
- Do NOT import Naive UI `as any` globals — use per-component imports + `create()` API.
- Do NOT use `onUpdatePageSize` in NDataTable pagination JS object — use `'onUpdate:pageSize': (size) => {}` (quoted).

## COMMANDS

```bash
cd frontend
npm run dev           # Vite dev server (default :5173)
npm run build         # Type-check + production build
npm run type-check    # vue-tsc only (no build)
npm run test:run      # Vitest (CI mode)
npm test              # Vitest (watch mode)
```

## NOTES

- TypeScript 6 with `node24` target.
- `@` alias maps to `src/` (configured in `vite.config.ts` and `vitest.config.ts`).
- Naive UI 2.44 is a large library — production build chunk was ~309 kB after tree-shaking (from ~711 kB raw).
- Vitest v4.x is configured with `jsdom` environment and `@vue/test-utils` v2.
- Frontend dev server proxies `/api` to backend at `localhost:8080` via `vite.config.ts`.
- There is no top-level `src/components/` directory — reusable view-specific components live under `src/views/*/components/`.
- `createDiscreteApi` from naive-ui is used in `request.ts` for message/notification without component context.
