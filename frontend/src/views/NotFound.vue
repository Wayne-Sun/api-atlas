<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NCard, NButton, NResult } from 'naive-ui'

const router = useRouter()
</script>

<template>
  <NCard style="max-width: 600px; margin: 80px auto; text-align: center;">
    <NResult status="404" title="404" description="页面不存在">
      <template #footer>
        <NButton type="primary" @click="router.push('/')">返回首页</NButton>
      </template>
    </NResult>
  </NCard>
</template>
