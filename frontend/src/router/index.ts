import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/datasource'
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

export default router
