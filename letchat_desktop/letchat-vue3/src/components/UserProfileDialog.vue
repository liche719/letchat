<template>
  <el-dialog
    v-model="visible"
    title="个人资料"
    width="520px"
    destroy-on-close
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="profile-form"
      @submit.prevent="save"
    >
      <div class="profile-head">
        <div class="lc-avatar profile-avatar">
          {{ (form.nickName || 'LC').slice(0, 2).toUpperCase() }}
        </div>
        <div>
          <strong>{{ form.nickName || 'LetChat 用户' }}</strong>
          <span>{{ userStore.userInfo?.userId }}</span>
        </div>
      </div>

      <el-form-item label="昵称" prop="nickName">
        <el-input
          id="profile-nickname"
          v-model="form.nickName"
          name="nickname"
          autocomplete="nickname"
          maxlength="20"
          show-word-limit
          aria-label="昵称"
        />
      </el-form-item>

      <el-form-item label="性别">
          <el-radio-group v-model="form.sex" aria-label="性别">
          <el-radio :label="0">女</el-radio>
          <el-radio :label="1">男</el-radio>
          <el-radio :label="2">保密</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="地区">
        <el-input
          id="profile-area"
          v-model="form.areaName"
          name="areaName"
          autocomplete="address-level2"
          placeholder="例如：上海"
          aria-label="地区"
        />
      </el-form-item>

      <el-form-item label="个性签名">
        <el-input
          id="profile-signature"
          v-model="form.personalSignature"
          name="personalSignature"
          autocomplete="off"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="写一句朋友能看到的话"
          aria-label="个性签名"
        />
      </el-form-item>

      <el-form-item label="加好友方式">
          <el-radio-group v-model="form.joinType" aria-label="加好友方式">
          <el-radio :label="0">允许直接添加</el-radio>
          <el-radio :label="1">需要我确认</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <p v-if="saveError" class="dialog-error" role="alert" aria-live="assertive">{{ saveError }}</p>

    <template #footer>
      <el-button :disabled="loading" @click="visible = false">取消</el-button>
      <el-button type="primary" native-type="submit" :loading="loading" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'profile-updated': []
}>()

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saveError = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const form = reactive({
  nickName: '',
  sex: 2,
  areaName: '',
  personalSignature: '',
  joinType: 1,
})

const rules: FormRules = {
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 20, message: '昵称长度 1-20 位', trigger: 'blur' },
  ],
}

function syncForm() {
  const user = userStore.userInfo
  if (!user) return
  form.nickName = user.nickName || ''
  form.sex = user.sex ?? 2
  form.areaName = user.areaName || ''
  form.personalSignature = user.personalSignature || ''
  form.joinType = user.joinType ?? 1
}

async function save() {
  if (loading.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  saveError.value = ''
  try {
    await userStore.updateUserInfo(form)
    ElMessage.success('资料已更新')
    emit('profile-updated')
    visible.value = false
  } catch {
    saveError.value = '资料保存失败，请重试'
  } finally {
    loading.value = false
  }
}

watch(() => userStore.userInfo, syncForm, { immediate: true })
watch(visible, (open) => {
  if (open) {
    saveError.value = ''
    syncForm()
  }
})
</script>

<style scoped>
.profile-form {
  padding-top: 2px;
}

.profile-head {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
  padding: 14px;
  border: 1px solid var(--lc-line-strong);
  border-left: 2px solid var(--lc-accent);
  border-radius: 14px;
  background: var(--lc-panel);
}

.profile-avatar {
  width: 56px;
  height: 56px;
  font-size: 17px;
}

.profile-head strong,
.profile-head span {
  display: block;
}

.profile-head strong {
  color: var(--lc-text);
  font-size: 18px;
  font-weight: 600;
}

.profile-head span {
  margin-top: 4px;
  color: var(--lc-muted);
  font-size: 12px;
}

.dialog-error {
  margin: 12px 0 0;
  color: var(--lc-danger);
  font-size: 13px;
}
</style>
