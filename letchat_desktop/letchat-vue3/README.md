# LetChat Vue3 前端应用

基于Vue3 + TypeScript + Vite + Element Plus构建的聊天应用前端。

## 项目特性

- ✅ 用户注册/登录
- ✅ 实时聊天（WebSocket）
- ✅ 好友管理
- ✅ 群组聊天
- ✅ 文件传输（图片/视频）
- ✅ 消息历史记录
- ✅ 响应式设计

## 技术栈

- **Vue 3** - 渐进式JavaScript框架
- **TypeScript** - 类型安全的JavaScript
- **Vite** - 下一代前端构建工具
- **Element Plus** - Vue3组件库
- **Pinia** - 状态管理
- **Vue Router** - 路由管理
- **Axios** - HTTP客户端
- **Socket.IO Client** - WebSocket客户端

## 项目结构

```
src/
├── api/              # API接口定义
├── assets/           # 静态资源
├── components/       # 公共组件
├── stores/           # 状态管理（Pinia）
├── types/            # TypeScript类型定义
├── utils/            # 工具函数
├── views/            # 页面组件
├── App.vue           # 根组件
├── main.ts           # 入口文件
└── router/index.ts   # 路由配置
```

## 快速开始

### 安装依赖

```bash
npm install
```

### 开发环境运行

```bash
npm run dev
```

### 生产环境构建

```bash
npm run build
```

### 代码检查

```bash
npm run lint
```

## API配置

在 `.env` 文件中配置API地址：

```
VITE_API_BASE_URL=/api
VITE_WS_BASE_URL=/socket.io
```

**注意**: 项目已配置Vite代理，自动解决CORS跨域问题，无需修改后端配置。

## 功能模块

### 1. 用户认证
- 用户注册（邮箱验证）
- 用户登录（验证码验证）
- 自动登录（Token持久化）

### 2. 聊天功能
- 实时消息收发（WebSocket）
- 文本消息
- 图片/视频消息
- 消息历史记录
- 未读消息计数

### 3. 联系人管理
- 搜索联系人
- 添加好友
- 好友申请处理
- 删除好友
- 黑名单管理

### 4. 群组功能
- 创建群组
- 群组聊天
- 群成员管理
- 退出群组

## 开发规范

### 代码风格
- 使用ESLint + Prettier进行代码格式化
- 遵循Vue3 Composition API规范
- 使用TypeScript进行类型检查

### 组件规范
- 组件名使用PascalCase
- 文件名使用kebab-case
- Props和Emits使用TypeScript类型定义

### 状态管理
- 使用Pinia进行状态管理
- Store按功能模块划分
- 避免直接修改store状态
