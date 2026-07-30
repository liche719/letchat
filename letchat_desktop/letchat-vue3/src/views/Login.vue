<template>
  <main class="auth-shell">
    <AuthPreview />

    <section class="auth-console">
      <header class="auth-toolbar">
        <div class="auth-brand">
          <span class="brand-mark" aria-hidden="true"><ChatDotRound /></span>
          <strong translate="no">LetChat</strong>
        </div>
        <ThemeToggle />
      </header>

      <section class="auth-panel">
        <h1>欢迎回来</h1>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="auth-form"
          @keyup.enter="handleLogin"
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

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              name="password"
              autocomplete="current-password"
              placeholder="请输入密码…"
              show-password
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
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form>

        <div class="switch-line">
          <span>还没有账号？</span>
          <RouterLink to="/register">立即注册</RouterLink>
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
import { ChatDotRound, Key, Lock, Message } from '@element-plus/icons-vue'
import AuthPreview from '@/components/AuthPreview.vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { useUserStore } from '@/stores/user'

defineOptions({ name: 'LoginView' })

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaUrl = ref('')
const checkCodeKey = ref('')
const captchaUnavailable = ref(false)

const form = reactive({
  email: '',
  password: '',
  checkCode: '',
})

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  checkCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}

async function refreshCaptcha() {
  captchaUnavailable.value = false
  try {
    const captcha = await userStore.getCheckCode()
    captchaUrl.value = captcha.checkCode
    checkCodeKey.value = captcha.checkCodeKey
  } catch {
    captchaUrl.value = ''
    checkCodeKey.value = ''
    captchaUnavailable.value = true
  }
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login(form.email, form.password, form.checkCode, checkCodeKey.value)
    ElMessage.success('登录成功')
    router.push('/')
  } catch {
    form.checkCode = ''
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>
