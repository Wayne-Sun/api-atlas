<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import type { UserInfo, UserCreateDTO, UserUpdateDTO } from '@/stores/user'
import { NModal, NForm, NFormItem, NInput, NSelect, NButton, NSpace, useMessage } from 'naive-ui'

const props = defineProps<{
  show: boolean
  user: UserInfo | null
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  saved: []
}>()

const store = useUserStore()
const message = useMessage()
const formRef = ref<InstanceType<typeof NForm> | null>(null)
const submitting = ref(false)

const roleOptions = [
  { label: '管理员', value: 'ADMIN' },
  { label: '普通用户', value: 'USER' },
]

const statusOptions = [
  { label: '启用', value: 'ENABLED' },
  { label: '禁用', value: 'DISABLED' },
]

const form = ref<{
  username: string
  displayName: string
  password: string
  role: string
  status: string
}>({
  username: '',
  displayName: '',
  password: '',
  role: 'USER',
  status: 'ENABLED',
})

const isEdit = ref(false)

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度 3-50 个字符', trigger: 'blur' },
  ],
  displayName: [
    { required: true, message: '请输入显示名称', trigger: 'blur' },
  ],
  password: [
    {
      required: true,
      message: '请设置密码',
      trigger: 'blur',
      validator: (_rule: unknown, value: string) => {
        if (isEdit.value && !value) return Promise.resolve()
        if (!value) return Promise.reject('请设置密码')
        if (value.length < 6 || value.length > 100) return Promise.reject('密码长度 6-100 个字符')
        return Promise.resolve()
      },
    },
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' },
  ],
}

watch(() => props.show, (val) => {
  if (val) {
    if (props.user) {
      isEdit.value = true
      form.value = {
        username: props.user.username,
        displayName: props.user.displayName,
        password: '',
        role: props.user.role,
        status: props.user.status,
      }
    } else {
      isEdit.value = false
      form.value = {
        username: '',
        displayName: '',
        password: '',
        role: 'USER',
        status: 'ENABLED',
      }
    }
    formRef.value?.restoreValidation()
  }
})

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value && props.user) {
      const data: UserUpdateDTO = {
        displayName: form.value.displayName,
        role: form.value.role,
      }
      await store.updateUser(props.user.id, data)
      message.success('更新成功')
    } else {
      const data: UserCreateDTO = {
        username: form.value.username,
        displayName: form.value.displayName,
        password: form.value.password,
        role: form.value.role,
      }
      await store.createUser(data)
      message.success('创建成功')
    }
    emit('saved')
    emit('update:show', false)
  } catch (e) {
    console.warn('保存用户失败:', e)
    // handled by interceptor
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  emit('update:show', false)
}
</script>

<template>
  <NModal :show="show" @update:show="handleClose" preset="card" :title="isEdit ? '编辑用户' : '创建用户'" style="max-width: 520px;">
    <NForm ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100px">
      <NFormItem label="用户名" path="username">
        <NInput v-model:value="form.username" placeholder="请输入用户名" :disabled="isEdit" />
      </NFormItem>
      <NFormItem label="显示名称" path="displayName">
        <NInput v-model:value="form.displayName" placeholder="请输入显示名称" />
      </NFormItem>
      <NFormItem label="密码" path="password">
        <NInput
          v-model:value="form.password"
          type="password"
          show-password-on="click"
          :placeholder="isEdit ? '留空则不修改密码' : '请设置密码'"
        />
      </NFormItem>
      <NFormItem label="角色" path="role">
        <NSelect v-model:value="form.role" :options="roleOptions" />
      </NFormItem>
      <NFormItem v-if="!isEdit" label="状态" path="status">
        <NSelect v-model:value="form.status" :options="statusOptions" />
      </NFormItem>
    </NForm>
    <template #footer>
      <NSpace justify="end">
        <NButton @click="handleClose">取消</NButton>
        <NButton type="primary" @click="handleSave" :loading="submitting">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
