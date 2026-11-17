<template>
  <div class="cs-window">
    <!-- 顶部栏：头像 + 标题 + 操作 -->
    <div class="cs-header">
      <div class="cs-brand">
        <div class="cs-avatar">
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <circle cx="24" cy="24" r="24" fill="#ffffff" />
            <path d="M24 12c-6 0-10 4.4-10 10v3.2c0 1 .8 1.8 1.8 1.8s1.8-.8 1.8-1.8V22c0-3.8 2.7-6.6 6.4-6.6s6.4 2.8 6.4 6.6v3.2c0 1 .8 1.8 1.8 1.8s1.8-.8 1.8-1.8V22c0-5.6-4-10-10-10z" fill="#5b8fc9"/>
            <path d="M34 25h-2v4c0 1.1-.9 2-2 2h-6a1.6 1.6 0 100 3.2h6c2.9 0 5.2-2.3 5.2-5.2v-2.4c0-.9-.7-1.6-1.2-1.6z" fill="#5b8fc9"/>
            <circle cx="24" cy="27" r="7.4" fill="#ffd9c7"/>
            <path d="M16.6 26.2c0-4.1 3.3-7.4 7.4-7.4s7.4 3.3 7.4 7.4c0 .7-.6 1.2-1.2 1l-1.6-.5c-1.4-.4-2.3-1.7-2.3-3.1v-.6l-2.3 1.1-2.3-1.1v.6c0 1.4-.9 2.7-2.3 3.1l-1.6.5c-.6.2-1.2-.3-1.2-1z" fill="#3f3a52"/>
            <circle cx="21" cy="27.4" r="1" fill="#3f3a52"/>
            <circle cx="27" cy="27.4" r="1" fill="#3f3a52"/>
            <path d="M22.4 31c1 .8 2.2.8 3.2 0" stroke="#c9765f" stroke-width="1.4" stroke-linecap="round" fill="none"/>
            <path d="M15.2 30.5c1.6 4.3 4.9 6.8 8.8 6.8s7.2-2.5 8.8-6.8c-2.4 1.3-5.4 2-8.8 2s-6.4-.7-8.8-2z" fill="#5b8fc9"/>
          </svg>
        </div>
        <div class="cs-brand-text">
          <div class="cs-title">{{ label('title', '在线咨询') }}</div>
          <div class="cs-subtitle">AI 智能客服 · 实时响应</div>
        </div>
      </div>

      <div class="cs-actions">
        <button class="cs-icon-btn" type="button" title="新会话" @click="newSession">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M20 11a8 8 0 10-2.3 5.7" />
            <path d="M20 4v7h-7" />
          </svg>
        </button>
        <button class="cs-icon-btn" type="button" title="收起" @click="closeWindow">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round">
            <path d="M5 12h14" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 消息区 -->
    <div ref="scrollBox" class="cs-body">
      <!-- 欢迎区 -->
      <template v-if="messages.length === 0">
        <div class="cs-msg cs-msg-ai">
          <div class="cs-meta">在线客服 {{ nowText }}</div>
          <div class="cs-bubble">
            <p>您好，欢迎使用跨境电商智能助手。</p>
            <p>我可以解答平台使用、翻译、术语库、商品发布、搜索结果、合规检查等问题，也能帮您查找商品。</p>
            <p>知识库已收录 {{ stats.faq_count || 0 }} 条常见问题与 {{ stats.product_count || 0 }} 件商品。</p>
          </div>
        </div>

        <div class="cs-samples">
          <button v-for="q in samples" :key="q" class="cs-chip" type="button" @click="askSample(q)">
            {{ q }}
          </button>
        </div>
      </template>

      <!-- 历史消息 -->
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="cs-msg"
        :class="message.sender === 'user' ? 'cs-msg-user' : 'cs-msg-ai'"
      >
        <div class="cs-meta">
          {{ message.sender === 'user' ? '我' : '在线客服' }} {{ message.time }}
        </div>
        <div class="cs-bubble">
          <p class="cs-text"><template
            v-for="(seg, si) in segments(message.content)"
            :key="si"
          ><strong v-if="seg.bold">{{ seg.text }}</strong><template v-else>{{ seg.text }}</template></template></p>

          <div v-if="message.sources && message.sources.length" class="cs-sources">
            <span class="cs-sources-label">参考来源</span>
            <span
              v-for="(s, i) in message.sources"
              :key="i"
              class="cs-source"
              :class="s.type === 'faq' ? 'is-faq' : 'is-product'"
              :title="s.title"
            >
              {{ s.type === 'faq' ? '知识库' : '商品' }} · {{ Math.round((s.score || 0) * 100) }}%
            </span>
          </div>

          <p v-if="message.needHuman" class="cs-hint">建议联系人工客服进一步处理</p>
        </div>
      </div>

      <!-- 输入中 -->
      <div v-if="loading" class="cs-msg cs-msg-ai">
        <div class="cs-meta">在线客服 {{ nowText }}</div>
        <div class="cs-bubble cs-typing">
          <span></span><span></span><span></span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="cs-input-area">
      <input
        v-model="inputMessage"
        class="cs-input"
        type="text"
        :placeholder="label('inputPlaceholder', '请输入内容')"
        :disabled="loading"
        @keyup.enter="sendMessage"
      />
      <div class="cs-input-bar">
        <div class="cs-emoji-wrap">
          <button
            class="cs-emoji"
            type="button"
            title="表情"
            aria-haspopup="listbox"
            :aria-expanded="emojiPanelOpen ? 'true' : 'false'"
            @click.stop="toggleEmojiPanel"
          >😊</button>
          <div v-if="emojiPanelOpen" class="cs-emoji-panel" role="listbox" aria-label="表情">
            <button
              v-for="e in EMOJI_LIST"
              :key="e"
              class="cs-emoji-item"
              type="button"
              role="option"
              @click="appendEmoji(e)"
            >{{ e }}</button>
          </div>
        </div>
        <button
          class="cs-send"
          :class="{ 'is-active': canSend }"
          type="button"
          :disabled="!canSend"
          @click="sendMessage"
        >
          {{ loading ? '发送中' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'

export default {
  name: 'AIAssistant',
  emits: ['close'],
  props: {
    t: { type: Object, default: () => ({}) }
  },
  setup(props, { emit }) {
    const inputMessage = ref('')
    const messages = ref([])
    const loading = ref(false)
    const scrollBox = ref(null)
    const stats = ref({ faq_count: 0, product_count: 0, ready: false })
    const sessionId = ref('')

    const samples = ['怎么发布商品？', '翻译支持哪些语言', '术语库不生效怎么办', '有没有纯棉连衣裙']

    const canSend = computed(() => !!inputMessage.value.trim() && !loading.value)

    const label = (key, fallback) => {
      const dict = props.t && props.t.aiAssistant
      return (dict && dict[key]) || fallback
    }

    const pad = (n) => String(n).padStart(2, '0')
    const formatTime = (d = new Date()) =>
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    const nowText = formatTime()

    const scrollToBottom = async () => {
      await nextTick()
      if (scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight
    }

    const newSession = () => {
      sessionId.value = `s_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
      messages.value = []
      inputMessage.value = ''
    }

    const closeWindow = () => emit('close')

    // 表情面板：点表情按钮弹出候选面板，选中后插入输入框。
    // 原实现是「点一次直接追加一个 😊」，没有可选性，不符合表情按钮的通用预期。
    const EMOJI_LIST = [
      '😊', '😄', '😃', '🙂', '😉', '😍', '🤔', '😅',
      '👍', '🙏', '👌', '🎉', '❤️', '🔥', '⭐', '✅',
      '😢', '😮', '😴', '🤝', '📦', '🚚', '💰', '❓'
    ]
    const emojiPanelOpen = ref(false)

    const toggleEmojiPanel = () => {
      emojiPanelOpen.value = !emojiPanelOpen.value
    }

    // 连续选多个表情时不关闭面板；点外部或再次点按钮才收起
    const appendEmoji = (emoji) => {
      inputMessage.value += emoji
    }

    const onEmojiDocDown = (event) => {
      if (!emojiPanelOpen.value) return
      const wrap = event.target && event.target.closest && event.target.closest('.cs-emoji-wrap')
      if (!wrap) emojiPanelOpen.value = false
    }

    /**
     * 轻量渲染 Markdown 加粗：把 **xxx** 拆成片段交给模板渲染 <strong>。
     * 不使用 v-html，避免模型输出里的标签被当作 HTML 执行。
     */
    const segments = (text) => {
      const raw = String(text || '').trim()
      const parts = raw.split(/\*\*(.+?)\*\*/g)
      return parts
        .filter((s) => s !== '')
        .map((s, i) => ({ text: s, bold: i % 2 === 1 }))
    }

    const loadStats = async () => {
      try {
        const { getAxiosInstance } = await import('../api/index.js')
        const axios = await getAxiosInstance()
        const resp = await axios.get('/api/assistant/stats')
        if (resp.data && resp.data.data) stats.value = resp.data.data
      } catch (e) {
        // 未登录或无权限时静默：统计仅用于欢迎语展示
      }
    }

    const askSample = (q) => {
      inputMessage.value = q
      sendMessage()
    }

    const sendMessage = async () => {
      const text = inputMessage.value.trim()
      if (!text || loading.value) return

      messages.value.push({ sender: 'user', content: text, time: formatTime() })
      inputMessage.value = ''
      loading.value = true
      await scrollToBottom()

      try {
        const { getAxiosInstance } = await import('../api/index.js')
        const axios = await getAxiosInstance()
        const resp = await axios.post('/api/assistant/chat', {
          message: text,
          session_id: sessionId.value
        })
        const data = (resp.data && resp.data.data) || {}
        messages.value.push({
          sender: 'ai',
          content: data.reply || '抱歉，暂时无法回答这个问题。',
          sources: data.sources || [],
          needHuman: !!data.need_human,
          time: formatTime()
        })
      } catch (error) {
        const status = error && error.response && error.response.status
        messages.value.push({
          sender: 'ai',
          content: status === 401
            ? '登录状态已失效，请重新登录后再试。'
            : '服务暂时不可用，请稍后重试。',
          sources: [],
          time: formatTime()
        })
      } finally {
        loading.value = false
        await scrollToBottom()
      }
    }

    onMounted(() => {
      newSession()
      loadStats()

      // 演示/截图辅助：?ask=问题 可直接发起一次提问
      const preset = new URLSearchParams(window.location.search).get('ask')
      if (preset) {
        inputMessage.value = preset
        sendMessage()
      }

      document.addEventListener('mousedown', onEmojiDocDown)
    })

    onUnmounted(() => {
      document.removeEventListener('mousedown', onEmojiDocDown)
    })

    return {
      inputMessage, messages, loading, scrollBox, stats, samples, canSend,
      nowText, label, sendMessage, newSession, closeWindow, askSample, appendEmoji,
      EMOJI_LIST, emojiPanelOpen, toggleEmojiPanel,
      segments
    }
  }
}
</script>

<style scoped>
.cs-window {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #ffffff;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
}

/* ===== 顶部栏 ===== */
.cs-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  background: linear-gradient(150deg, #5f7ff0 0%, #3b5bdb 55%, #2f4bc4 100%);
  color: #fff;
  flex-shrink: 0;
}
.cs-brand { display: flex; align-items: center; gap: 12px; }
.cs-avatar {
  width: 46px; height: 46px;
  border-radius: 50%;
  background: #fff;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, .12);
  overflow: hidden;
}
.cs-avatar svg { width: 42px; height: 42px; display: block; }
.cs-brand-text { display: flex; flex-direction: column; line-height: 1.25; }
.cs-title { font-size: 15.5px; font-weight: 650; letter-spacing: .2px; }
.cs-subtitle { font-size: 11.5px; opacity: .82; margin-top: 1px; }

.cs-actions { display: flex; align-items: center; gap: 6px; }
.cs-icon-btn {
  width: 30px; height: 30px;
  border: none; border-radius: 6px;
  background: rgba(255, 255, 255, .16);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: background .18s ease;
}
.cs-icon-btn:hover { background: rgba(255, 255, 255, .3); }

/* ===== 消息区 ===== */
.cs-body {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  padding: 18px 18px 8px;
}
.cs-body::-webkit-scrollbar { width: 6px; }
.cs-body::-webkit-scrollbar-thumb { background: #d8dee6; border-radius: 3px; }

.cs-msg { display: flex; flex-direction: column; margin-bottom: 22px; }
.cs-msg-ai { align-items: flex-start; }
.cs-msg-user { align-items: flex-end; }

.cs-meta {
  font-size: 12px;
  color: #9aa3ad;
  margin-bottom: 8px;
  padding: 0 4px;
}

.cs-bubble {
  max-width: 86%;
  background: #f2f2f2;
  border-radius: 8px;
  padding: 16px 20px;
  color: #2f3339;
  font-size: 16px;
  line-height: 1.8;
  word-break: break-word;
}
.cs-bubble p { margin: 0 0 8px; }
.cs-bubble p:last-child { margin-bottom: 0; }

.cs-msg-user .cs-bubble {
  background: var(--brand);
  color: #fff;
  border-radius: 8px;
}

.cs-text { white-space: pre-wrap; }
.cs-text strong { font-weight: 700; }

/* 参考来源 */
.cs-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e6e6e6;
}
.cs-sources-label { font-size: 11px; color: #9aa3ad; }
.cs-source {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 10px;
  background: #e8eef6;
  color: #5a7fa8;
  max-width: 190px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cs-source.is-product { background: #e9f5ec; color: #4b8a5f; }

.cs-hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: #c07a2c;
}

/* 欢迎区快捷问题 */
.cs-samples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 18px 4px;
}
.cs-chip {
  border: 1px solid #d9e3ef;
  background: #f7fafd;
  color: #4a6b8a;
  font-size: 13px;
  padding: 7px 12px;
  border-radius: 16px;
  cursor: pointer;
  transition: all .18s ease;
}
.cs-chip:hover { background: var(--brand-soft); border-color: var(--brand-200); color: var(--brand); }

/* 输入中动画 */
.cs-typing { display: flex; gap: 5px; align-items: center; padding: 18px 20px; }
.cs-typing span {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #b6bec8;
  animation: cs-bounce 1.2s infinite ease-in-out;
}
.cs-typing span:nth-child(2) { animation-delay: .18s; }
.cs-typing span:nth-child(3) { animation-delay: .36s; }
@keyframes cs-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .55; }
  30% { transform: translateY(-4px); opacity: 1; }
}

/* ===== 输入区 ===== */
.cs-input-area {
  flex-shrink: 0;
  border-top: 1px solid #edf0f3;
  padding: 14px 18px 12px;
  background: #fff;
}
.cs-input {
  width: 100%;
  border: none;
  outline: none;
  font-size: 16px;
  color: #2f3339;
  padding: 4px 2px 10px;
  background: transparent;
}
.cs-input::placeholder { color: #b8c0c9; }

.cs-input-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 6px;
}
.cs-emoji {
  border: none;
  background: transparent;
  font-size: 20px;
  cursor: pointer;
  color: #8a939d;
  padding: 2px 4px;
  line-height: 1;
}
.cs-emoji:hover { opacity: .75; }

/* 表情面板：点按钮向上弹出，8 列网格，外层容器负责定位与点外部关闭 */
.cs-emoji-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.cs-emoji-panel {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 0;
  width: 236px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  padding: 8px;
  background: #ffffff;
  border: 1px solid #e4e7ee;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(20, 24, 33, .14);
  z-index: 30;
}

.cs-emoji-item {
  border: none;
  background: transparent;
  font-size: 17px;
  line-height: 1;
  padding: 6px 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background .12s ease, transform .12s ease;
}

.cs-emoji-item:hover {
  background: #f2f4f9;
  transform: scale(1.12);
}

.cs-send {
  min-width: 78px;
  padding: 8px 20px;
  border: none;
  border-radius: 4px;
  background: #d8e8f8;
  color: #ffffff;
  font-size: 15px;
  cursor: not-allowed;
  transition: background .18s ease;
}
.cs-send.is-active {
  background: var(--brand);
  cursor: pointer;
}
.cs-send.is-active:hover { background: var(--brand-hover); }
</style>
