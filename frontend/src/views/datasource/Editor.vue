<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useDatasourceStore } from '@/stores/datasource'
import { NForm, NFormItem, NInput, NInputNumber, NSelect, NButton, NSpace, NCard, useMessage } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const store = useDatasourceStore()
const message = useMessage()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const testLoading = ref(false)

const typeOptions: { label: string; value: string }[] = [
  { label: 'MySQL', value: 'MySQL' },
  { label: 'PostgreSQL', value: 'PostgreSQL' },
  { label: 'Elasticsearch', value: 'Elasticsearch' },
  { label: 'MongoDB', value: 'MongoDB' }
]

const formRef = ref()
const form = ref({
  name: '',
  type: 'MySQL',
  host: '',
  port: 3306,
  databaseName: '',
  username: '',
  password: '',
  apiKey: ''
})

const isElasticsearch = computed(() => form.value.type === 'Elasticsearch')

// Only flip the default MySQL port (3306) when switching to MongoDB — never clobber a user-customized port
watch(
  () => form.value.type,
  (t) => {
    if (t === 'MongoDB' && form.value.port === 3306) form.value.port = 27017
  }
)

const rules = {
  name: [{ required: true, message: '请输入名称' }],
  type: [{ required: true, message: '请选择类型' }],
  host: [{ required: true, message: '请输入主机地址' }],
  port: [{ required: true, message: '请输入端口' }]
}

onMounted(async () => {
  if (isEdit.value) {
    const id = Number(route.params.id)
    try {
      await store.getById(id)
      if (store.current) {
        const data = store.current
        form.value = {
          name: data.name,
          type: data.type,
          host: data.host,
          port: data.port,
          databaseName: data.databaseName ?? '',
          username: data.username ?? '',
          password: data.password ?? '',
          apiKey: data.apiKey ?? ''
        }
      }
    } catch (e) {
      console.warn('加载数据源失败:', e)
      message.error('加载数据源失败')
      // handled by interceptor
    }
  }
})

async function handleTestConnection() {
  testLoading.value = true
  try {
    const res = await store.testConnection({
      type: form.value.type,
      host: form.value.host,
      port: form.value.port,
      databaseName: form.value.databaseName,
      username: form.value.username,
      password: form.value.password,
      apiKey: form.value.apiKey
    })
    if (res.data?.connected) {
      message.success(`连接成功 (${res.data.responseTime}ms)`)
    } else {
      message.error('连接失败: ' + (res.data?.error || 'Unknown error'))
    }
  } catch (e) {
    console.warn('连接测试失败:', e)
    message.error('连接测试失败')
  } finally {
    testLoading.value = false
  }
}

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    if (isEdit.value) {
      await store.update(Number(route.params.id), form.value)
      message.success('更新成功')
    } else {
      await store.create(form.value)
      message.success('创建成功')
    }
    router.push('/datasource')
  } catch (e) {
    console.warn('保存数据源失败:', e)
    // Error already handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleCancel() {
  router.push('/datasource')
}
</script>

<template>
  <NCard :title="isEdit ? '编辑数据源' : '新增数据源'" style="max-width: 600px; margin: 0 auto;">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100px">
      <NFormItem label="名称" path="name">
        <NInput v-model:value="form.name" placeholder="请输入数据源名称" />
      </NFormItem>
      <NFormItem label="类型" path="type">
        <NSelect v-model:value="form.type" :options="typeOptions" />
      </NFormItem>
      <NFormItem label="主机" path="host">
        <NInput v-model:value="form.host" placeholder="例如: localhost" />
      </NFormItem>
      <NFormItem label="端口" path="port">
        <NInputNumber v-model:value="form.port" placeholder="例如: 3306" style="width: 100%" />
      </NFormItem>
      <NFormItem v-if="!isElasticsearch" label="数据库名" path="databaseName">
        <NInput v-model:value="form.databaseName" placeholder="数据库名称" />
      </NFormItem>
      <NFormItem v-if="!isElasticsearch" label="用户名" path="username">
        <NInput v-model:value="form.username" placeholder="用户名" />
      </NFormItem>
      <NFormItem v-if="!isElasticsearch" label="密码" path="password">
        <NInput v-model:value="form.password" type="password" placeholder="密码" />
      </NFormItem>
      <NFormItem v-if="isElasticsearch" label="API Key" path="apiKey">
        <NInput v-model:value="form.apiKey" placeholder="Elasticsearch API Key" />
      </NFormItem>
      <NSpace justify="center" style="margin-top: 24px;">
        <NButton @click="handleTestConnection" :loading="testLoading">测试连接</NButton>
        <NButton type="primary" @click="handleSave" :loading="loading">保存</NButton>
        <NButton @click="handleCancel">取消</NButton>
      </NSpace>
    </NForm>
  </NCard>
</template>
