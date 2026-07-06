<template>
  <main class="auth-shell compact">
    <section class="auth-panel">
      <div class="brand">
        <div class="brand-mark">LC</div>
        <div>
          <p class="eyebrow">LetChat Workspace</p>
          <h1>创建账号</h1>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @keyup.enter="handleRegister">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" size="large" placeholder="name@example.com" :prefix-icon="Message" />
        </el-form-item>

        <el-form-item label="昵称" prop="nickName">
          <el-input v-model="form.nickName" size="large" placeholder="你想让朋友看到的名字" :prefix-icon="User" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="8-18 位，包含字母和数字" :prefix-icon="Lock" />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" size="large" type="password" show-password placeholder="再次输入密码" :prefix-icon="Lock" />
        </el-form-item>

        <el-form-item label="验证码" prop="checkCode">
          <div class="captcha-row">
            <el-input v-model="form.checkCode" size="large" placeholder="4 位验证码" :prefix-icon="Key" />
            <button
              class="captcha"
              :class="{ unavailable: captchaUnavailable }"
              type="button"
              @click="refreshCaptcha"
              title="刷新验证码"
            >
              <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" />
              <Refresh v-else :size="18" />
            </button>
          </div>
        </el-form-item>

        <el-button type="primary" size="large" class="submit" :loading="loading" @click="handleRegister">
          注册
        </el-button>
      </el-form>

      <div class="switch-line">
        <span>已有账号？</span>
        <button type="button" @click="router.push('/login')">返回登录</button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, Refresh, User } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaUrl = ref('')
const checkCodeKey = ref('')
const captchaUnavailable = ref(false)

const form = reactive({
  email: '',
  nickName: '',
  password: '',
  confirmPassword: '',
  checkCode: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度 2-20 位', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*\d)(?=.*[a-zA-Z])[\da-zA-Z~!@#$%^&*_]{8,18}$/, message: '密码需 8-18 位，包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  checkCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function refreshCaptcha() {
  captchaUnavailable.value = false
  try {
    const response = await authApi.getCheckCode()
    captchaUrl.value = response.data.checkCode
    checkCodeKey.value = response.data.checkCodeKey
  } catch {
    captchaUrl.value = ''
    checkCodeKey.value = ''
    captchaUnavailable.value = true
  }
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.register(form.email, form.password, form.nickName, form.checkCode, checkCodeKey.value)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch {
    form.checkCode = ''
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.auth-shell {
  display: grid;
  place-items: center;
  min-height: 100vh;
  padding: 24px;
}

.auth-panel {
  width: min(460px, 100%);
  padding: 34px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.84);
  box-shadow: var(--lc-shadow);
  backdrop-filter: blur(22px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 16px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 18px;
  color: #ffffff;
  font-size: 18px;
  font-weight: 900;
  background: linear-gradient(145deg, #0f8f76, #123b34);
}

.eyebrow,
h1 {
  margin: 0;
}

.eyebrow {
  margin-bottom: 4px;
  color: var(--lc-accent);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h1 {
  font-size: 32px;
  font-weight: 900;
}

.auth-form {
  margin-top: 26px;
}

.captcha-row {
  display: grid;
  grid-template-columns: 1fr 116px;
  gap: 10px;
  width: 100%;
}

.captcha {
  display: grid;
  place-items: center;
  height: 40px;
  border: 1px solid var(--lc-line);
  border-radius: 10px;
  background: #ffffff;
  cursor: pointer;
  overflow: hidden;
}

.captcha img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.captcha.unavailable {
  color: var(--lc-accent);
  background: var(--lc-accent-soft);
}

.submit {
  width: 100%;
  margin-top: 4px;
  border-radius: 12px;
  font-weight: 800;
}

.switch-line {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
  color: var(--lc-muted);
}

.switch-line button {
  border: 0;
  color: var(--lc-accent);
  font-weight: 800;
  background: transparent;
  cursor: pointer;
}
</style>
