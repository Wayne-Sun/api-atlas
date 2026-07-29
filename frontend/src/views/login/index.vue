<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { NForm, NFormItem, NInput, NButton, NCard, useMessage } from 'naive-ui'

const router = useRouter()
const authStore = useAuthStore()
const message = useMessage()

const username = ref('')
const password = ref('')

async function handleLogin() {
  try {
    await authStore.login(username.value, password.value)
    message.success('登录成功')
    router.push('/datasource')
  } catch (e) {
    console.warn('登录失败:', e)
    // handled by interceptor
  }
}
</script>

<template>
  <div style="display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f5f7fa;">
    <NCard title="登录" style="width: 400px;">
      <NForm>
        <NFormItem label="用户名">
          <NInput v-model:value="username" placeholder="请输入用户名" />
        </NFormItem>
        <NFormItem label="密码">
          <NInput v-model:value="password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
        </NFormItem>
        <NButton type="primary" block :loading="authStore.loading" @click="handleLogin">
          登录
        </NButton>
      </NForm>
    </NCard>
  </div>
</template>
