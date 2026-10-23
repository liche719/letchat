<template>
  <el-dialog
    v-model="visible"
    :title="group ? '编辑群聊' : '创建群聊'"
    width="460px"
    destroy-on-close
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    @closed="reset"
  >
    <form class="group-form" @submit.prevent="submit">
      <label for="group-name">
        <span>群聊名称</span>
        <el-input
          id="group-name"
          v-model="form.groupName"
          name="groupName"
          autocomplete="off"
          maxlength="20"
          show-word-limit
          placeholder="例如：周末电影小组"
          aria-label="群聊名称"
        />
      </label>

      <label for="group-notice">
        <span>群公告</span>
        <el-input
          id="group-notice"
          v-model="form.groupNotice"
          name="groupNotice"
          autocomplete="off"
          type="textarea"
          maxlength="200"
          :autosize="{ minRows: 3, maxRows: 5 }"
          show-word-limit
          placeholder="可选，写下群聊约定或活动安排"
          aria-label="群公告"
        />
      </label>

      <fieldset>
        <legend>入群方式</legend>
        <el-radio-group v-model="form.joinType" aria-label="入群方式">
          <el-radio :label="0">无需验证</el-radio>
          <el-radio :label="1">需群主同意</el-radio>
        </el-radio-group>
      </fieldset>

      <label v-if="!group" for="group-avatar" class="cover-picker">
        <span>群头像</span>
        <input
          id="group-avatar"
          name="avatarFile"
          type="file"
          accept="image/*"
          aria-label="群头像"
          @change="selectAvatar"
        />
        <small>{{ avatarFile ? avatarFile.name : '请选择一张图片作为群头像' }}</small>
      </label>

      <p v-else class="helper">修改名称、公告或入群方式后，会同步显示给所有成员。</p>
      <p v-if="submitError" class="dialog-error" role="alert" aria-live="assertive">{{ submitError }}</p>

      <div class="actions">
        <el-button :disabled="submitting" @click="visible = false">取消</el-button>
        <el-button type="primary" native-type="submit" :loading="submitting">
          {{ group ? '保存修改' : '创建群聊' }}
        </el-button>
      </div>
    </form>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { contactApi } from '@/api/contact'
import type { GroupInfo } from '@/types/api'

const props = defineProps<{
  modelValue: boolean
  group?: GroupInfo | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const form = reactive({ groupName: '', groupNotice: '', joinType: 0 })
const avatarFile = ref<File | null>(null)
const submitting = ref(false)
const submitError = ref('')

watch(
  () => [props.modelValue, props.group] as const,
  () => {
    if (!props.modelValue) return
    form.groupName = props.group?.groupName || ''
    form.groupNotice = props.group?.groupNotice || ''
    form.joinType = props.group?.joinType ?? 0
    avatarFile.value = null
  },
  { immediate: true },
)

function selectAvatar(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('群头像不能超过 5 MB')
    input.value = ''
    return
  }
  avatarFile.value = file
}

async function submit() {
  submitError.value = ''
  const groupName = form.groupName.trim()
  if (!groupName) {
    ElMessage.warning('请填写群聊名称')
    return
  }
  if (!props.group && !avatarFile.value) {
    ElMessage.warning('请上传群头像')
    return
  }

  const data = new FormData()
  if (props.group?.groupId) data.append('groupId', props.group.groupId)
  data.append('groupName', groupName)
  data.append('groupNotice', form.groupNotice.trim())
  data.append('joinType', String(form.joinType))
  if (avatarFile.value) {
    data.append('avatarFile', avatarFile.value)
    data.append('avatarCover', avatarFile.value)
  }

  submitting.value = true
  try {
    await contactApi.saveGroup(data)
    ElMessage.success(props.group ? '群聊已更新' : '群聊已创建')
    visible.value = false
    emit('saved')
  } catch {
    submitError.value = props.group ? '群聊更新失败，请重试' : '群聊创建失败，请重试'
  } finally {
    submitting.value = false
  }
}

function reset() {
  avatarFile.value = null
  submitError.value = ''
}
</script>

<style scoped>
.group-form {
  display: grid;
  gap: 18px;
}

label,
fieldset {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  border: 0;
}

label > span,
legend {
  color: var(--lc-text);
  font-size: 14px;
  font-weight: 600;
}

.cover-picker input {
  color: var(--lc-muted);
  font-size: 13px;
}

small,
.helper,
.dialog-error {
  margin: 0;
  color: var(--lc-muted);
  font-size: 12px;
  line-height: 1.6;
}

.dialog-error {
  color: var(--lc-danger);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}
</style>
