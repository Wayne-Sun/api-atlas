<script setup lang="ts">
import { ref, h, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  NLayout, NLayoutSider, NLayoutHeader, NLayoutContent,
  NMenu, NIcon, NText, NTag, NButton, NSpace,
} from 'naive-ui'
import { DatabaseOutlined, ApiOutlined, UserOutlined } from '@vicons/antd'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const collapsed = ref(false)

const menuOptions = computed(() => {
  const options = [
    {
      label: '数据源管理',
      key: '/datasource',
      icon: () => h(NIcon, null, { default: () => h(DatabaseOutlined) })
    },
    {
      label: '接口管理',
      key: '/interface',
      icon: () => h(NIcon, null, { default: () => h(ApiOutlined) })
    }
  ]
  if (authStore.isAdmin) {
    options.push({
      label: '用户管理',
      key: '/user',
      icon: () => h(NIcon, null, { default: () => h(UserOutlined) })
    })
  }
  return options
})

function getSelectedKey(): string {
  if (route.path.startsWith('/datasource')) return '/datasource'
  if (route.path.startsWith('/interface')) return '/interface'
  if (route.path.startsWith('/user')) return '/user'
  return ''
}

function handleMenuUpdate(key: string) {
  router.push(key)
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<template>
  <NLayout has-sider style="height: 100vh">
    <NLayoutSider
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="220"
      :collapsed="collapsed"
      show-trigger
      @collapse="collapsed = true"
      @expand="collapsed = false"
    >
      <div style="height: 64px; display: flex; align-items: center; justify-content: center;">
        <NText :style="{ color: '#3B82F6', fontWeight: 'bold', fontSize: collapsed ? '14px' : '18px' }">
          {{ collapsed ? 'AA' : 'API Atlas' }}
        </NText>
      </div>
      <NMenu
        :collapsed="collapsed"
        :collapsed-width="64"
        :collapsed-icon-size="22"
        :value="getSelectedKey()"
        :options="menuOptions"
        @update:value="handleMenuUpdate"
      />
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader
        bordered
        style="height: 48px; display: flex; align-items: center; padding: 0 24px; justify-content: space-between;"
      >
        <NText>API Atlas 管理控制台</NText>
        <template v-if="authStore.isAuthenticated">
          <NSpace align="center" :size="12">
            <NTag :type="authStore.isAdmin ? 'info' : 'default'" size="small">
              {{ authStore.isAdmin ? 'ADMIN' : 'USER' }}
            </NTag>
            <NText>{{ authStore.currentUser?.displayName }}</NText>
            <NButton text type="primary" @click="handleLogout">退出登录</NButton>
          </NSpace>
        </template>
      </NLayoutHeader>
      <NLayoutContent style="padding: 24px;">
        <router-view />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>
