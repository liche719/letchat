<template>
  <main class="auth-shell">
    <AuthPreview />

    <section class="auth-console register-console">
      <header class="auth-toolbar">
        <div class="auth-brand">
          <span class="brand-mark" aria-hidden="true"><ChatDotRound /></span>
          <strong translate="no">LetChat</strong>
        </div>
        <ThemeToggle />
      </header>

      <section class="auth-panel">
        <h1>创建账号</h1>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="auth-form"
          @keyup.enter="handleRegister"
        >
          <el-form-item label="邮箱" prop="email">
            <el-input
              v-model="form.email"
              size="large"
              type="email"
              name="email"
              autocomplete="email"
              inputmode="email"
              spellcheck="false"
              placeholder="name@example.com"
              :prefix-icon="Message"
            />
          </el-form-item>

          <el-form-item label="昵称" prop="nickName">
            <el-input
              v-model="form.nickName"
              size="large"
              name="nickname"
              autocomplete="nickname"
              placeholder="朋友看到的名字…"
              :prefix-icon="User"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              name="password"
              autocomplete="new-password"
              show-password
              placeholder="8-18 位，包含字母和数字…"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              size="large"
              type="password"
              name="confirmPassword"
              autocomplete="new-password"
              show-password
              placeholder="再次输入密码…"
              :prefix-icon="Lock"
            />
          </el-form-item>

          <el-form-item label="验证码" prop="checkCode">
            <div class="captcha-row">
              <el-input
                v-model="form.checkCode"
                size="large"
                name="checkCode"
                autocomplete="off"
                inputmode="text"
                spellcheck="false"
                placeholder="请输入 4 位验证码…"
                :prefix-icon="Key"
              />
              <button
                class="captcha"
                :class="{ unavailable: captchaUnavailable }"
                type="button"
                @click="refreshCaptcha"
                :title="captchaUnavailable ? '验证码加载失败，点击重试' : '刷新验证码'"
                :aria-label="captchaUnavailable ? '验证码加载失败，点击重试' : '刷新图形验证码'"
              >
                <img v-if="captchaUrl" :src="captchaUrl" alt="图形验证码" width="110" height="40" />
                <span v-else class="captcha-fallback">{{ captchaUnavailable ? '点击刷新' : '加载中…' }}</span>
              </button>
            </div>
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit"
            :loading="loading"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form>

        <div class="switch-line">
          <span>已有账号？</span>
          <RouterLink to="/login">返回登录</RouterLink>
        </div>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Key, Lock, Message, User } from '@element-plus/icons-vue'
import AuthPreview from '@/components/AuthPreview.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { authApi } from '@/api/auth'

defineOptions({ name: 'RegisterView' })

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
    {
      pattern: /^(?=.*\d)(?=.*[a-zA-Z])[\da-zA-Z~!@#$%^&*_]{8,18}$/,
      message: '密码需 8-18 位，包含字母和数字',
      trigger: 'blur',
    },
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
    const captcha = await authApi.getCheckCode()
    captchaUrl.value = captcha.checkCode
    checkCodeKey.value = captcha.checkCodeKey
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
    await authApi.register(
      form.email,
      form.password,
      form.nickName,
      form.checkCode,
      checkCodeKey.value,
    )
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
