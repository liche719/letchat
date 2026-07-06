<template>
  <main class="auth-shell">
    <section class="auth-panel">
      <div class="brand">
        <div class="brand-mark">LC</div>
        <div>
          <p class="eyebrow">LetChat Workspace</p>
          <h1>欢迎回来</h1>
        </div>
      </div>

      <p class="intro">登录后会自动建立 WebSocket 长连接，同步离线消息、好友申请和最近会话。</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="auth-form" @keyup.enter="handleLogin">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" size="large" placeholder="name@example.com" :prefix-icon="Message" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            placeholder="请输入密码"
            show-password
            :prefix-icon="Lock"
          />
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

        <el-button type="primary" size="large" class="submit" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <div class="switch-line">
        <span>还没有账号？</span>
        <button type="button" @click="router.push('/register')">创建新账号</button>
      </div>
    </section>

    <aside class="auth-copy">
      <div class="signal">
        <span class="signal-dot"></span>
        <span>Message queue ready</span>
      </div>
      <h2>把聊天链路做成一个可观察的实时系统。</h2>
      <p>联系人、消息状态、离线补偿和 WebSocket 心跳都在一个清爽界面里工作。</p>
      <div class="metric-row">
        <div>
          <strong>MQ</strong>
          <span>异步持久化</span>
        </div>
        <div>
          <strong>WS</strong>
          <span>实时投递</span>
        </div>
        <div>
          <strong>DLQ</strong>
          <span>失败补偿</span>
        </div>
      </div>
    </aside>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Key, Lock, Message, Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

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

<style scoped>
.auth-shell {
  display: grid;
  grid-template-columns: minmax(360px, 440px) minmax(0, 1fr);
  min-height: 100vh;
  padding: 32px;
  gap: 28px;
}

.auth-panel {
  align-self: center;
  padding: 36px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--lc-shadow);
  backdrop-filter: blur(22px);
  animation: panel-in 0.5s ease both;
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

.eyebrow {
  margin: 0 0 4px;
  color: var(--lc-accent);
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 34px;
  font-weight: 900;
  letter-spacing: 0;
}

.intro {
  margin-top: 18px;
  color: var(--lc-muted);
}

.auth-form {
  margin-top: 28px;
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
  background: #fff;
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
  margin-top: 6px;
  border-radius: 12px;
  font-weight: 800;
}

.switch-line {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 22px;
  color: var(--lc-muted);
}

.switch-line button {
  border: 0;
  color: var(--lc-accent);
  font-weight: 800;
  background: transparent;
  cursor: pointer;
}

.auth-copy {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-height: calc(100vh - 64px);
  padding: 52px;
  border-radius: 26px;
  color: #f7fffc;
  overflow: hidden;
  background:
    linear-gradient(rgba(16, 40, 34, 0.68), rgba(16, 40, 34, 0.82)),
    url('https://images.unsplash.com/photo-1557804506-669a67965ba0?auto=format&fit=crop&w=1600&q=80') center/cover;
}

.auth-copy::before {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, transparent, rgba(255, 255, 255, 0.12));
}

.auth-copy > * {
  position: relative;
}

.signal {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
}

.signal-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #34d399;
  box-shadow: 0 0 0 6px rgba(52, 211, 153, 0.18);
}

.auth-copy h2 {
  max-width: 720px;
  margin-top: 24px;
  font-size: clamp(38px, 5vw, 74px);
  line-height: 0.98;
  font-weight: 900;
  letter-spacing: 0;
}

.auth-copy p {
  max-width: 540px;
  margin-top: 22px;
  color: rgba(247, 255, 252, 0.82);
  font-size: 17px;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 34px;
  max-width: 540px;
}

.metric-row div {
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.1);
}

.metric-row strong,
.metric-row span {
  display: block;
}

.metric-row strong {
  font-size: 22px;
  font-weight: 900;
}

.metric-row span {
  margin-top: 4px;
  color: rgba(247, 255, 252, 0.72);
}

@keyframes panel-in {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 900px) {
  .auth-shell {
    grid-template-columns: 1fr;
    padding: 18px;
  }

  .auth-copy {
    display: none;
  }

  .auth-panel {
    align-self: center;
  }
}
</style>
