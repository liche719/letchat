<template>
  <div class="test-container">
    <h1>LetChat Vue3 测试页面</h1>
    <div class="test-section">
      <h2>组件测试</h2>
      <el-button @click="testLogin" type="primary">测试登录</el-button>
      <el-button @click="testRegister" type="success">测试注册</el-button>
      <el-button @click="testChat" type="info">测试聊天</el-button>
    </div>
    
    <div class="test-section">
      <h2>环境变量测试</h2>
      <p>API地址: {{ apiUrl }}</p>
      <p>WebSocket地址: {{ wsUrl }}</p>
    </div>
    
    <div class="test-section">
      <h2>图标测试</h2>
      <div class="icon-test">
        <el-icon size="20"><User /></el-icon>
        <el-icon size="20"><ChatDotRound /></el-icon>
        <el-icon size="20"><Picture /></el-icon>
        <el-icon size="20"><InfoFilled /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const apiUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:7070/api'
const wsUrl = import.meta.env.VITE_WS_BASE_URL || 'http://localhost:7071'

const testLogin = () => {
  router.push('/login')
}

const testRegister = () => {
  router.push('/register')
}

const testChat = () => {
  router.push('/')
}
</script>

<style scoped>
.test-container {
  max-width: 600px;
  margin: 50px auto;
  padding: 20px;
  text-align: center;
}

.test-section {
  margin: 30px 0;
  padding: 20px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
}

.test-section h2 {
  margin-bottom: 15px;
  color: #409eff;
}

.icon-test {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin: 20px 0;
}
</style>