<script setup lang="ts">
import { ref, h } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NLayout, NLayoutSider, NLayoutHeader, NLayoutContent, NMenu, NIcon, NText } from 'naive-ui'
import { DatabaseOutlined, ApiOutlined } from '@vicons/antd'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)

const menuOptions = [
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

function handleMenuUpdate(key: string) {
  router.push(key)
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
        :value="route.path.startsWith('/datasource') ? '/datasource' : '/interface'"
        :options="menuOptions"
        @update:value="handleMenuUpdate"
      />
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader bordered style="height: 48px; display: flex; align-items: center; padding: 0 24px;">
        <NText>API Atlas 管理控制台</NText>
      </NLayoutHeader>
      <NLayoutContent style="padding: 24px;">
        <router-view />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>
