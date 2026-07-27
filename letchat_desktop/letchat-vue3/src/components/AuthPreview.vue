<template>
  <aside class="auth-preview" aria-hidden="true">
    <div class="preview-window" aria-hidden="true">
      <div class="preview-sidebar">
        <div class="preview-title">
          <strong>消息</strong>
          <span>2</span>
        </div>
        <div class="preview-contact active">
          <span class="preview-avatar">林</span>
          <span class="preview-contact-main">
            <strong>小林</strong>
            <small>那就周六见 👋</small>
          </span>
          <time>刚刚</time>
        </div>
        <div class="preview-contact">
          <span class="preview-avatar warm">陈</span>
          <span class="preview-contact-main">
            <strong>阿陈</strong>
            <small>图片</small>
          </span>
          <time>18:20</time>
        </div>
        <div class="preview-contact">
          <span class="preview-avatar soft">家</span>
          <span class="preview-contact-main">
            <strong>家人群</strong>
            <small>晚上记得早点回来</small>
          </span>
          <time>昨天</time>
        </div>
      </div>

      <div class="preview-chat">
        <header>
          <span class="preview-avatar">林</span>
          <span class="preview-person">
            <strong>小林</strong>
            <small>好友</small>
          </span>
        </header>
        <div class="preview-messages">
          <p class="preview-message received">周末一起吃饭吗？</p>
          <p class="preview-message sent">好呀，还是老地方。</p>
          <p class="preview-message received">那就周六见 👋</p>
        </div>
        <footer>
          <span>输入消息</span>
          <ChatDotRound />
        </footer>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ChatDotRound } from '@element-plus/icons-vue'
</script>

<style scoped>
.auth-preview {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  padding: clamp(24px, 4vw, 48px);
  border: 1px solid color-mix(in srgb, var(--lc-accent) 24%, var(--lc-line));
  border-radius: 24px;
  color: var(--lc-text);
  background:
    radial-gradient(
      circle at 80% 16%,
      color-mix(in srgb, var(--lc-accent) 18%, transparent),
      transparent 34%
    ),
    radial-gradient(
      circle at 12% 84%,
      color-mix(in srgb, var(--lc-brass) 12%, transparent),
      transparent 36%
    ),
    var(--lc-surface-subtle);
  overflow: hidden;
}

.auth-preview::after {
  position: absolute;
  right: -90px;
  bottom: -110px;
  width: 300px;
  height: 300px;
  border: 1px solid color-mix(in srgb, var(--lc-accent) 18%, transparent);
  border-radius: 50%;
  content: '';
  pointer-events: none;
}

.preview-window {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(168px, 0.42fr) minmax(280px, 1fr);
  width: min(100%, 760px);
  height: min(68vh, 520px);
  margin: auto;
  border: 1px solid var(--lc-line);
  border-radius: 22px;
  background: color-mix(in srgb, var(--lc-surface) 94%, transparent);
  box-shadow: var(--lc-shadow);
  backdrop-filter: blur(14px);
  overflow: hidden;
}

.preview-sidebar {
  padding: 18px 10px;
  border-right: 1px solid var(--lc-line);
  background: color-mix(in srgb, var(--lc-panel) 74%, var(--lc-surface));
}

.preview-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px 14px;
}

.preview-title strong {
  font-size: 18px;
}

.preview-title span {
  display: grid;
  place-items: center;
  min-width: 20px;
  height: 20px;
  border-radius: 999px;
  color: var(--lc-on-accent);
  background: var(--lc-accent);
  font-size: 11px;
}

.preview-contact {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 10px 8px;
  border-radius: 12px;
}

.preview-contact.active {
  background: var(--lc-surface);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--lc-text) 7%, transparent);
}

.preview-contact-main,
.preview-contact-main strong,
.preview-contact-main small {
  display: block;
  min-width: 0;
}

.preview-contact-main strong {
  font-size: 13px;
}

.preview-contact-main small {
  margin-top: 3px;
  overflow: hidden;
  color: var(--lc-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}

.preview-contact time {
  align-self: start;
  color: var(--lc-soft);
  font-size: 10px;
}

.preview-chat {
  display: flex;
  min-width: 0;
  flex-direction: column;
  background: var(--lc-surface-subtle);
}

.preview-chat header {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--lc-line);
  background: var(--lc-surface);
}

.preview-avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  color: var(--lc-on-accent);
  background: var(--lc-accent);
  font-weight: 700;
}

.preview-contact .preview-avatar {
  width: 36px;
  height: 36px;
  font-size: 12px;
}

.preview-avatar.warm {
  background: var(--lc-brass);
}

.preview-avatar.soft {
  color: var(--lc-text);
  background: var(--lc-panel-strong);
}

.preview-person,
.preview-person strong,
.preview-person small {
  display: block;
}

.preview-person strong {
  font-size: 14px;
}

.preview-person small {
  margin-top: 3px;
  color: var(--lc-muted);
  font-size: 11px;
}

.preview-messages {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  justify-content: center;
  padding: 24px 18px;
}

.preview-message {
  width: fit-content;
  max-width: 78%;
  margin: 0;
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.5;
}

.preview-message.received {
  border-bottom-left-radius: 4px;
  background: var(--lc-panel);
}

.preview-message.sent {
  align-self: flex-end;
  border-bottom-right-radius: 4px;
  color: var(--lc-on-accent);
  background: var(--lc-accent);
}

.preview-chat footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 56px;
  margin: 0 14px 14px;
  padding: 0 14px;
  border: 1px solid var(--lc-line);
  border-radius: 14px;
  color: var(--lc-soft);
  background: var(--lc-surface);
  font-size: 12px;
}

.preview-chat footer :deep(svg) {
  width: 19px;
  height: 19px;
  color: var(--lc-accent);
}

@media (max-width: 1100px) {
  .preview-window {
    grid-template-columns: 150px minmax(250px, 1fr);
  }

  .preview-contact time {
    display: none;
  }
}

@media (max-width: 900px) {
  .auth-preview {
    display: none;
  }
}
</style>
