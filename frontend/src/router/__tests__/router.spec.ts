import { describe, it, expect, beforeEach } from 'vitest'
import router from '@/router/index.ts'

describe('router configuration', () => {
  beforeEach(() => {
    router.push('/')
  })

  describe('route definitions', () => {
    it('has 4 top-level routes', () => {
      const rawRoutes = router.options.routes
      expect(rawRoutes.length).toBe(4)
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
  })
})
