import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import router from '@/router/index.ts'

describe('router configuration', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  describe('route definitions', () => {
    it('has 6 top-level routes', () => {
      const rawRoutes = router.options.routes
      expect(rawRoutes.length).toBe(6)
    })

    it('root path redirects to /datasource', () => {
      const rawRoutes = router.options.routes
      const rootRoute = rawRoutes.find((r) => r.path === '/')!
      expect(rootRoute.redirect).toBe('/datasource')
    })
  })

  describe('datasource routes', () => {
    it('DatasourceList is at /datasource', () => {
      const resolved = router.resolve({ name: 'DatasourceList' })
      expect(resolved.name).toBe('DatasourceList')
      expect(resolved.path).toBe('/datasource')
    })

    it('DatasourceCreate is at /datasource/create', () => {
      const resolved = router.resolve({ name: 'DatasourceCreate' })
      expect(resolved.name).toBe('DatasourceCreate')
      expect(resolved.path).toBe('/datasource/create')
    })

    it('DatasourceEdit is at /datasource/edit/:id', () => {
      const resolved = router.resolve({ name: 'DatasourceEdit', params: { id: '123' } })
      expect(resolved.name).toBe('DatasourceEdit')
      expect(resolved.path).toBe('/datasource/edit/123')
      expect(resolved.params.id).toBe('123')
    })
  })

  describe('interface routes', () => {
    it('InterfaceList is at /interface', () => {
      const resolved = router.resolve({ name: 'InterfaceList' })
      expect(resolved.name).toBe('InterfaceList')
      expect(resolved.path).toBe('/interface')
    })

    it('InterfaceCreate is at /interface/create', () => {
      const resolved = router.resolve({ name: 'InterfaceCreate' })
      expect(resolved.name).toBe('InterfaceCreate')
      expect(resolved.path).toBe('/interface/create')
    })

    it('InterfaceEdit is at /interface/edit/:id', () => {
      const resolved = router.resolve({ name: 'InterfaceEdit', params: { id: '456' } })
      expect(resolved.name).toBe('InterfaceEdit')
      expect(resolved.path).toBe('/interface/edit/456')
      expect(resolved.params.id).toBe('456')
    })

    it('InterfaceTest is at /interface/test/:id', () => {
      const resolved = router.resolve({ name: 'InterfaceTest', params: { id: '789' } })
      expect(resolved.name).toBe('InterfaceTest')
      expect(resolved.path).toBe('/interface/test/789')
      expect(resolved.params.id).toBe('789')
    })
  })

  describe('login route', () => {
    it('Login is at /login', () => {
      const resolved = router.resolve({ name: 'Login' })
      expect(resolved.name).toBe('Login')
      expect(resolved.path).toBe('/login')
    })
  })

  describe('user route', () => {
    it('UserList is at /user', () => {
      const resolved = router.resolve({ name: 'UserList' })
      expect(resolved.name).toBe('UserList')
      expect(resolved.path).toBe('/user')
    })

    it('requiresAuth and requiresAdmin meta tags', () => {
      const rawRoutes = router.options.routes
      const userRoute = rawRoutes.find((r) => r.path === '/user')!
      expect(userRoute.meta).toEqual({ requiresAuth: true, requiresAdmin: true })
    })
  })

  describe('404 catch-all route', () => {
    it('matches unmatched paths', () => {
      const resolved = router.resolve('/some/random/path')
      expect(resolved.name).toBe('NotFound')
    })

    it('is the last top-level route', () => {
      const rawRoutes = router.options.routes
      const lastRoute = rawRoutes[rawRoutes.length - 1]
      expect(lastRoute.name).toBe('NotFound')
      expect(lastRoute.path).toBe('/:pathMatch(.*)*')
    })
  })

  describe('lazy loading', () => {
    it('all route components are lazy-loaded (defined as functions in config)', () => {
      const rawRoutes = router.options.routes

      function checkLazyLoading(routes: typeof rawRoutes) {
        for (const route of routes) {
          if (route.component) {
            expect(typeof route.component).toBe('function')
          }
          if (route.children) {
            checkLazyLoading(route.children)
          }
        }
      }

      checkLazyLoading(rawRoutes)
    })

    it('datasource child components are lazy-loaded', () => {
      const rawRoutes = router.options.routes
      const datasourceRoute = rawRoutes.find((r) => r.path === '/datasource')!
      expect(typeof datasourceRoute.component).toBe('function')

      for (const child of datasourceRoute.children!) {
        expect(typeof child.component).toBe('function')
      }
    })

    it('interface child components are lazy-loaded', () => {
      const rawRoutes = router.options.routes
      const interfaceRoute = rawRoutes.find((r) => r.path === '/interface')!
      expect(typeof interfaceRoute.component).toBe('function')

      for (const child of interfaceRoute.children!) {
        expect(typeof child.component).toBe('function')
      }
    })

    it('user child components are lazy-loaded', () => {
      const rawRoutes = router.options.routes
      const userRoute = rawRoutes.find((r) => r.path === '/user')!
      expect(typeof userRoute.component).toBe('function')

      for (const child of userRoute.children!) {
        expect(typeof child.component).toBe('function')
      }
    })
  })
})
