<template>
  <el-dialog
    v-model="visible"
    title="个人资料"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="profileFormRef"
      :model="profileForm"
      :rules="rules"
      label-width="80px"
      v-loading="loading"
    >
      <el-form-item label="头像">
        <el-upload
          class="avatar-uploader"
          :action="uploadUrl"
          :show-file-list="false"
          :on-success="handleAvatarSuccess"
          :before-upload="beforeAvatarUpload"
          :headers="uploadHeaders"
          :data="{ isAvatarUpload: true }"
        >
          <img v-if="profileForm.avatar" :src="profileForm.avatar" class="avatar" />
          <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
        </el-upload>
        <div class="upload-tip">点击上传头像</div>
      </el-form-item>

      <el-form-item label="昵称" prop="nickName">
        <el-input v-model="profileForm.nickName" maxlength="20" show-word-limit />
      </el-form-item>

      <el-form-item label="性别">
        <el-radio-group v-model="profileForm.sex">
          <el-radio :label="0">女</el-radio>
          <el-radio :label="1">男</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="地区">
        <el-input v-model="profileForm.areaName" placeholder="请输入地区" />
      </el-form-item>

      <el-form-item label="个性签名">
        <el-input
          v-model="profileForm.personalSignature"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="请输入个性签名"
        />
      </el-form-item>

      <el-form-item label="加好友方式">
        <el-radio-group v-model="profileForm.joinType">
          <el-radio :label="0">直接加入</el-radio>
          <el-radio :label="1">需要验证</el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSave" :loading="loading">
        保存修改
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types/api'
import { processResponse } from '@/utils/responseHandler'
import { useUploadHeaders, getUploadUrl } from '@/utils/uploadConfig'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'profile-updated': [userInfo: UserInfo]
}>()

const userStore = useUserStore()
const profileFormRef = ref()
const loading = ref(false)

// 获取上传配置
const uploadHeaders = useUploadHeaders()
const uploadUrl = getUploadUrl('/userInfo/saveUserInfo')

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const profileForm = ref<Partial<UserInfo>>({
  nickName: '',
  sex: 0,
  areaName: '',
  personalSignature: '',
  joinType: 0,
  avatar: ''
})

const rules = {
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 1, max: 20, message: '昵称长度1-20位', trigger: 'blur' }
  ]
}

// 监听用户信息变化
watch(
  () => userStore.userInfo,
  (newUserInfo) => {
    if (newUserInfo) {
      profileForm.value = {
        nickName: newUserInfo.nickName || '',
        sex: newUserInfo.sex || 0,
        areaName: newUserInfo.areaName || '',
        personalSignature: newUserInfo.personalSignature || '',
        joinType: newUserInfo.joinType || 0,
        avatar: newUserInfo.avatar || ''
      }
    }
  },
  { immediate: true }
)

// 头像上传前验证
const beforeAvatarUpload = (file: File) => {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png'
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isJPG) {
    ElMessage.error('头像只能是 JPG/PNG 格式!')
  }
  if (!isLt2M) {
    ElMessage.error('头像大小不能超过 2MB!')
  }
  return isJPG && isLt2M
}

// 处理头像上传成功
const handleAvatarSuccess = (response: any, file: File) => {
  console.log('头像上传成功:', response)
  
  // 处理响应
  const processed = processResponse(response)
  if (processed.success && processed.data) {
    ElMessage.success('头像上传成功')
    // 更新头像URL
    if (typeof processed.data === 'string') {
      profileForm.value.avatar = processed.data
    } else if (processed.data.avatar) {
      profileForm.value.avatar = processed.data.avatar
    }
  } else {
    ElMessage.error(processed.message || '头像上传失败')
  }
}

// 处理头像上传
const handleAvatarUpload = async (options: any) => {
  // 这里可以实现头像上传逻辑
  // 由于API需要MultipartFile，这里简化处理
  ElMessage.info('头像上传功能将在后续版本中支持')
}

// 保存用户信息
const handleSave = async () => {
  if (!profileFormRef.value) return

  await profileFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    loading.value = true
    try {
      await userStore.updateUserInfo(profileForm.value)
      ElMessage.success('个人资料更新成功')
      emit('profile-updated', userStore.userInfo!)
      visible.value = false
    } catch (error: any) {
      console.error('更新用户信息失败:', error)
      ElMessage.error(error.message || '更新失败')
    } finally {
      loading.value = false
    }
  })
}

const handleClose = () => {
  // 重置表单
  if (userStore.userInfo) {
    profileForm.value = {
      nickName: userStore.userInfo.nickName || '',
      sex: userStore.userInfo.sex || 0,
      areaName: userStore.userInfo.areaName || '',
      personalSignature: userStore.userInfo.personalSignature || '',
      joinType: userStore.userInfo.joinType || 0,
      avatar: userStore.userInfo.avatar || ''
    }
  }
}
</script>

<style scoped>
.avatar-uploader {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
  text-align: center;
}

.avatar-uploader:hover {
  border-color: var(--el-color-primary);
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 80px;
  height: 80px;
  text-align: center;
  line-height: 80px;
  border-radius: 50%;
}

.avatar {
  width: 80px;
  height: 80px;
  display: block;
  object-fit: cover;
  border-radius: 50%;
}

.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

:deep(.el-form-item__label) {
  font-weight: bold;
}

:deep(.el-dialog__body) {
  padding-top: 10px;
}
</style>