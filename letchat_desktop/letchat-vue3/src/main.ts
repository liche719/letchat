import 'element-plus/dist/index.css'
import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSegmented,
  ElTag,
  ElTooltip,
  vLoading,
} from 'element-plus'

import App from './App.vue'
import router from './router'

const app = createApp(App)

const elementComponents = [
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSegmented,
  ElTag,
  ElTooltip,
]

elementComponents.forEach((component) => app.use(component))

app.use(createPinia())
app.use(router)
app.directive('loading', vLoading)

app.mount('#app')
