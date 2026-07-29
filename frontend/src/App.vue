<script setup lang="ts">
import { onMounted } from 'vue'
import { NConfigProvider, NMessageProvider } from 'naive-ui'
import type { GlobalThemeOverrides } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#3B82F6',
    primaryColorHover: '#60A5FA',
    primaryColorPressed: '#2563EB',
    primaryColorSuppl: '#3B82F6'
  }
}

onMounted(() => {
  const authStore = useAuthStore()
  authStore.initFromStorage()
})
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides">
    <NMessageProvider>
      <router-view />
    </NMessageProvider>
  </NConfigProvider>
</template>
