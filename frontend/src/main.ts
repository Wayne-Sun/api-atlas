import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import naiveUiPlugin from '@/utils/naive-ui'

const app = createApp(App)
app.use(router)
app.use(createPinia())
app.use(naiveUiPlugin)
app.config.errorHandler = (err, instance, info) => {
  console.error('Vue error:', err)
  console.error('Component:', instance?.$options?.name || instance)
  console.error('Info:', info)
}

app.mount('#app')
