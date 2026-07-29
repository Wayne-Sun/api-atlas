import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/datasource'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue')
  },
  {
    path: '/user',
    component: () => import('@/layouts/BaseLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'UserList',
        component: () => import('@/views/user/index.vue')
      }
    ]
  },
  {
    path: '/datasource',
    component: () => import('@/layouts/BaseLayout.vue'),
    children: [
      {
        path: '',
        name: 'DatasourceList',
        component: () => import('@/views/datasource/index.vue')
      },
      {
        path: 'create',
        name: 'DatasourceCreate',
        component: () => import('@/views/datasource/Editor.vue')
      },
      {
        path: 'edit/:id',
        name: 'DatasourceEdit',
        component: () => import('@/views/datasource/Editor.vue')
      }
    ]
  },
  {
    path: '/interface',
    component: () => import('@/layouts/BaseLayout.vue'),
    children: [
      {
        path: '',
        name: 'InterfaceList',
        component: () => import('@/views/interface/index.vue')
      },
      {
        path: 'create',
        name: 'InterfaceCreate',
        component: () => import('@/views/interface/Editor.vue')
      },
      {
        path: 'edit/:id',
        name: 'InterfaceEdit',
        component: () => import('@/views/interface/Editor.vue')
      },
      {
        path: 'test/:id',
        name: 'InterfaceTest',
        component: () => import('@/views/interface/TestView.vue')
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to, _from, next) => {
  const authStore = useAuthStore()

  // Public routes that don't need auth
  if (to.name === 'Login') {
    next()
    return
  }

  // Check authentication
  if (!authStore.isAuthenticated) {
    next({ name: 'Login' })
    return
  }

  // Fetch currentUser on page refresh (token exists but user is null)
  if (!authStore.currentUser && authStore.token) {
    await authStore.fetchMe()
    if (!authStore.isAuthenticated) {
      next({ name: 'Login' })
      return
    }
  }

  // Check admin routes
  if (to.meta?.requiresAdmin && !authStore.isAdmin) {
    next({ name: 'DatasourceList' })
    return
  }

  next()
})

export default router
