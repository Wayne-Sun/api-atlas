<script setup lang="ts">
import { h, ref, onMounted, computed, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useInterfaceStore } from '@/stores/interface'
import { useDatasourceStore } from '@/stores/datasource'
import { NForm, NFormItem, NInput, NInputNumber, NSelect, NButton, NSwitch, NSpace, NDataTable, NCard, useMessage } from 'naive-ui'

const router = useRouter()
const route = useRoute()
const interfaceStore = useInterfaceStore()
const datasourceStore = useDatasourceStore()
const message = useMessage()

const isEdit = computed(() => !!route.params.id)
const saving = ref(false)

// Form data
const formRef = ref()
const form = ref({
  englishName: '',
  chineseName: '',
  urlSlug: '',
  method: 'POST',
  dataSourceId: null as number | null,
  queryType: 'SQL',
  queryContent: '',
  isPaginated: false,
  pageSize: 10
})

// Auto-extracted parameters table
const paramColumns = [
  { title: '参数名', key: 'paramName' },
  {
    title: '类型',
    key: 'javaType',
    render: (row: LocalParam) => h(NSelect, {
      value: row.javaType,
      options: [
        { label: 'String', value: 'String' },
        { label: 'Integer', value: 'Integer' },
        { label: 'Long', value: 'Long' },
        { label: 'Double', value: 'Double' },
        { label: 'Boolean', value: 'Boolean' }
      ],
      'onUpdate:value': (val: string) => { row.javaType = val }
    })
  },
  {
    title: '备注',
    key: 'remark',
    render: (row: LocalParam) => h(NInput, {
      value: row.remark,
      'onUpdate:value': (val: string) => { row.remark = val }
    })
  }
]

interface LocalParam {
  id: number
  paramName: string
  javaType: string
  remark: string
  sortOrder: number
}

const params = ref<LocalParam[]>([])

// Datasource options (filtered by selected query type)
const datasourceOptions = computed(() => {
  return datasourceStore.list
    .filter(ds => {
      if (form.value.queryType === 'ESQL' || form.value.queryType === 'QUERY_DSL') {
        return ds.type === 'Elasticsearch'
      }
      if (form.value.queryType === 'MONGO_FIND' || form.value.queryType === 'MONGO_AGG') {
        return ds.type === 'MongoDB'
      }
      return ds.type === 'MySQL' || ds.type === 'PostgreSQL'
    })
    .map(ds => ({ label: `${ds.name} (${ds.type})`, value: ds.id }))
})

// Query type options derived from selected datasource
const queryTypeOptions = computed(() => {
  const selectedDs = datasourceStore.list.find(ds => ds.id === form.value.dataSourceId)
  if (selectedDs?.type === 'Elasticsearch') {
    return [
      { label: 'ES|QL', value: 'ESQL' },
      { label: 'Query DSL', value: 'QUERY_DSL' }
    ]
  }
  if (selectedDs?.type === 'MongoDB') {
    return [
      { label: 'Find', value: 'MONGO_FIND' },
      { label: 'Aggregation', value: 'MONGO_AGG' }
    ]
  }
  return [
    { label: 'SQL', value: 'SQL' },
    { label: 'IBATIS', value: 'IBATIS' }
  ]
})

// Extract params from query content
function extractParams() {
  const regex = /\$\{(\w+)\}/g
  const found = new Set<string>()
  let match
  while ((match = regex.exec(form.value.queryContent)) !== null) {
    if (match[1]) found.add(match[1])
  }
  // Merge: keep existing params + add new ones
  const existingNames = new Set(params.value.map(p => p.paramName))
  for (const name of found) {
    if (!existingNames.has(name)) {
      params.value.push({ id: 0, paramName: name, javaType: 'String', remark: '', sortOrder: 0 })
    }
  }
  // Remove params no longer in query
  params.value = params.value.filter(p => found.has(p.paramName))
}

// Watch query content for auto-extraction
watch(() => form.value.queryContent as string, () => {
  extractParams()
})

// Watch datasource change — auto-switch queryType if needed
watch(() => form.value.dataSourceId, (newId) => {
  const ds = datasourceStore.list.find(d => d.id === newId)
  if (ds?.type === 'Elasticsearch') {
    if (form.value.queryType !== 'ESQL' && form.value.queryType !== 'QUERY_DSL') {
      form.value.queryType = 'ESQL'
    }
  } else if (ds?.type === 'MongoDB') {
    if (form.value.queryType !== 'MONGO_FIND' && form.value.queryType !== 'MONGO_AGG') {
      form.value.queryType = 'MONGO_FIND'
    }
  } else if (ds?.type === 'MySQL' || ds?.type === 'PostgreSQL') {
    if (form.value.queryType === 'ESQL' || form.value.queryType === 'QUERY_DSL') {
      form.value.queryType = 'SQL'
    }
  }
})

// Watch queryType change — clear incompatible datasource
watch(() => form.value.queryType, (newType) => {
  if (newType === 'ESQL' || newType === 'QUERY_DSL') {
    const ds = datasourceStore.list.find(d => d.id === form.value.dataSourceId)
    if (ds && ds.type !== 'Elasticsearch') {
      form.value.dataSourceId = null
    }
  } else if (newType === 'MONGO_FIND' || newType === 'MONGO_AGG') {
    const ds = datasourceStore.list.find(d => d.id === form.value.dataSourceId)
    if (ds && ds.type !== 'MongoDB') {
      form.value.dataSourceId = null
    }
  } else if (newType === 'SQL' || newType === 'IBATIS') {
    const ds = datasourceStore.list.find(d => d.id === form.value.dataSourceId)
    if (ds && (ds.type === 'Elasticsearch' || ds.type === 'MongoDB')) {
      form.value.dataSourceId = null
    }
  }
})

const rules = {
  englishName: [{ required: true, message: '请输入英文名称' }],
  chineseName: [{ required: true, message: '请输入中文名称' }],
  urlSlug: [{ required: true, message: '请输入URL Slug' }],
  dataSourceId: [{ required: true, type: 'number' as const, message: '请选择数据源' }],
  queryContent: [{ required: true, message: '请输入查询内容' }]
}

onMounted(async () => {
  await datasourceStore.fetchList()

  if (isEdit.value) {
    const id = Number(route.params.id)
    try {
      await interfaceStore.getById(id)
      const data = interfaceStore.current
      if (data) {
        form.value = {
          englishName: data.englishName,
          chineseName: data.chineseName,
          urlSlug: data.urlSlug,
          method: data.method,
          dataSourceId: data.dataSourceId,
          queryType: data.queryType,
          queryContent: data.queryContent,
          isPaginated: data.isPaginated || false,
          pageSize: data.pageSize || 10
        }
        if (data.params) {
          params.value = data.params.map(p => ({
            id: p.id,
            paramName: p.paramName,
            javaType: p.javaType,
            remark: p.remark,
            sortOrder: p.sortOrder
          }))
        }
      }
    } catch (e) {
      console.warn('加载接口失败:', e)
      message.error('加载接口失败')
    }
  }
})

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      ...form.value,
      dataSourceId: form.value.dataSourceId ?? undefined,
      params: params.value
    }
    if (isEdit.value) {
      await interfaceStore.update(Number(route.params.id), payload)
      message.success('更新成功')
    } else {
      await interfaceStore.create(payload)
      message.success('创建成功')
    }
    router.push('/interface')
  } catch (e) {
    console.warn('保存接口失败:', e)
    // handled by interceptor
  } finally {
    saving.value = false
  }
}

function handleCancel() {
  router.push('/interface')
}
</script>

<template>
  <NCard :title="isEdit ? '编辑接口' : '新增接口'" style="max-width: 800px; margin: 0 auto;">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120px">
      <!-- Section 1: General Config -->
      <h3 style="margin-bottom: 16px;">通用配置</h3>
      <NFormItem label="英文名称" path="englishName">
        <NInput v-model:value="form.englishName" placeholder="例如: get_users" />
      </NFormItem>
      <NFormItem label="中文名称" path="chineseName">
        <NInput v-model:value="form.chineseName" placeholder="例如: 获取用户列表" />
      </NFormItem>
      <NFormItem label="URL Slug" path="urlSlug">
        <NInput v-model:value="form.urlSlug" placeholder="例如: /api/users" />
      </NFormItem>
      <NFormItem label="请求方法" path="method">
        <NSelect v-model:value="form.method" :options="[{ label: 'POST', value: 'POST' }, { label: 'GET', value: 'GET' }]" />
      </NFormItem>
      <NFormItem label="分页" path="isPaginated">
        <NSwitch v-model:value="form.isPaginated" />
      </NFormItem>
      <NFormItem v-if="form.isPaginated" label="每页条数" path="pageSize">
        <NInputNumber v-model:value="form.pageSize" style="width: 100%;" />
      </NFormItem>

      <!-- Section 2: Query Config -->
      <h3 style="margin-bottom: 16px; margin-top: 24px;">查询配置</h3>
      <NFormItem label="数据源" path="dataSourceId">
        <NSelect v-model:value="form.dataSourceId" :options="datasourceOptions" placeholder="选择数据源" />
      </NFormItem>
      <NFormItem label="查询类型" path="queryType">
        <NSelect v-model:value="form.queryType" :options="queryTypeOptions" />
      </NFormItem>
      <NFormItem label="查询内容" path="queryContent">
        <NInput v-model:value="form.queryContent" type="textarea" :rows="8" placeholder="输入查询语句，使用 ${paramName} 作为参数占位符" />
      </NFormItem>

      <!-- Section 3: Parameter Config -->
      <h3 style="margin-bottom: 16px; margin-top: 24px;">传参配置</h3>
      <NDataTable
        :columns="paramColumns"
        :data="params"
        :bordered="false"
        :max-height="200"
      />
      <div v-if="params.length === 0" style="text-align: center; color: #999; padding: 16px;">
        在查询内容中使用 ${paramName} 自动生成参数
      </div>

      <NSpace justify="center" style="margin-top: 24px;">
        <NButton type="primary" @click="handleSave" :loading="saving">保存</NButton>
        <NButton @click="handleCancel">取消</NButton>
      </NSpace>
    </NForm>
  </NCard>
</template>
