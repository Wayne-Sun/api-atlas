<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useInterfaceStore } from '@/stores/interface'
import type { TestResult } from '@/stores/interface'
import { useAuthStore } from '@/stores/auth'
import { NCard, NForm, NFormItem, NInput, NInputNumber, NButton, NSpace, NTag, useMessage } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const store = useInterfaceStore()
const authStore = useAuthStore()
const message = useMessage()

const loading = ref(false)
const iface = computed(() => store.current)

// Dynamic param inputs
const paramValues = ref<Record<string, string>>({})

// Page num/size for paginated interfaces
const pageNum = ref(1)
const pageSize = ref(10)

const result = ref<TestResult | null>(null)
const responseTime = ref<number | null>(null)

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    await store.getById(id)
    // Initialize param values
    if (iface.value?.params) {
      for (const p of iface.value.params) {
        paramValues.value[p.paramName] = ''
      }
    }
  } catch (e) {
    console.warn('加载接口失败:', e)
    message.error('加载接口失败')
  }
})

async function handleTest() {
  loading.value = true
  try {
    const res = await store.test(
      Number(route.params.id),
      paramValues.value,
      iface.value?.isPaginated ? pageNum.value : 0,
      iface.value?.isPaginated ? pageSize.value : 0
    )
    result.value = res?.data?.rows || res?.data
    responseTime.value = res?.data?.responseTime || null
  } catch (e) {
    console.warn('测试接口失败:', e)
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleReset() {
  if (iface.value?.params) {
    for (const p of iface.value.params) {
      paramValues.value[p.paramName] = ''
    }
  }
  result.value = null
  responseTime.value = null
}

async function handleOnline() {
  try {
    await store.updateStatus(Number(route.params.id), 'ONLINE')
    message.success('上线成功')
    router.push('/interface')
  } catch (e) {
    console.warn('上线失败:', e)
    // handled by interceptor
  }
}
</script>

<template>
  <NSpace vertical style="padding: 0;" :size="16">
    <!-- Interface info -->
    <NCard v-if="iface" :title="iface.chineseName || iface.englishName" size="small">
      <NSpace>
        <span><strong>URL:</strong> /api/interfaces/{{ iface.id }}/test</span>
        <span><strong>类型:</strong> {{ iface.queryType }}</span>
        <NTag :type="iface.status === 'ONLINE' ? 'success' : iface.status === 'PENDING_TEST' ? 'warning' : 'default'">
          {{ iface.status === 'PENDING_TEST' ? '待测试' : iface.status === 'ONLINE' ? '已上线' : '已下线' }}
        </NTag>
      </NSpace>
    </NCard>

    <NSpace :size="16" style="align-items: flex-start;">
      <!-- Left panel: Params form -->
      <NCard title="请求参数" style="flex: 1; min-width: 350px;">
        <NForm v-if="iface?.params?.length" label-placement="left" label-width="100px">
          <NFormItem v-for="param in iface.params" :key="param.paramName" :label="param.paramName">
            <NInput v-model:value="paramValues[param.paramName]" :placeholder="param.remark || param.paramName" />
          </NFormItem>
          <NFormItem v-if="iface.isPaginated" label="页码">
            <NInputNumber v-model:value="pageNum" style="width: 100%;" />
          </NFormItem>
          <NFormItem v-if="iface.isPaginated" label="每页条数">
            <NInputNumber v-model:value="pageSize" style="width: 100%;" />
          </NFormItem>
        </NForm>
        <div v-else style="color: #999; padding: 16px 0;">
          无需参数，直接测试
        </div>
        <NSpace>
          <NButton v-if="authStore.isAdmin" type="primary" @click="handleTest" :loading="loading">测试</NButton>
          <NButton @click="handleReset">重置</NButton>
        </NSpace>
      </NCard>

      <!-- Right panel: Result -->
      <NCard title="执行结果" style="flex: 2; min-width: 400px;">
        <div v-if="responseTime !== null" style="margin-bottom: 12px;">
          <NTag type="success">响应时间: {{ responseTime }}ms</NTag>
        </div>
        <pre v-if="result" style="background: #f5f5f5; padding: 12px; border-radius: 4px; overflow: auto; max-height: 500px; font-size: 13px;">{{ JSON.stringify(result, null, 2) }}</pre>
        <div v-else style="color: #999; padding: 32px 0; text-align: center;">
          点击"测试"按钮执行查询
        </div>
        <NSpace v-if="result && authStore.isAdmin" style="margin-top: 16px;">
          <NButton @click="router.push(`/interface/edit/${route.params.id}`)">返回编辑</NButton>
          <NButton v-if="iface?.status !== 'ONLINE'" type="primary" @click="handleOnline">提交上线</NButton>
        </NSpace>
      </NCard>
    </NSpace>
  </NSpace>
</template>
