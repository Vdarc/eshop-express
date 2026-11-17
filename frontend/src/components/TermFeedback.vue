<template>
  <div class="term-feedback">
    <div class="page-h">
      <div>
        <div class="tt">术语反哺</div>
        <div class="sub">从历史译文中发现反复出现、尚未入库的词，确认后沉淀为术语</div>
      </div>
    </div>

    <!-- 两个真实数字：都来自「我」的账号口径，不是后台全库 -->
    <div class="ui-stats">
      <div>
        <div class="v">{{ historyTotal === null ? '—' : historyTotal }}</div>
        <div class="k">我的翻译记录（条）</div>
      </div>
      <div>
        <div class="v">{{ glossaryTotal === null ? '—' : glossaryTotal }}</div>
        <div class="k">我的术语条目（条）</div>
      </div>
      <div>
        <div class="v">{{ ratioText }}</div>
        <div class="k">翻译 / 术语</div>
      </div>
    </div>

    <div class="ui-split-l" style="margin-top: 18px">
      <div>
        <SectionCard title="候选术语">
          <EmptyState
            icon="◌"
            title="暂无可确认的术语候选"
            desc="该功能正在建设中。系统会从你的历史译文中找出反复出现、尚未入库的词，供你确认后加入术语库。"
          >
            <template #actions>
              <button type="button" class="ui-btn ui-btn-sm ui-btn-sec" @click="changeRoute('glossary')">
                去术语库
              </button>
              <button type="button" class="ui-btn ui-btn-sm ui-btn-sec" @click="changeRoute('translate')">
                去翻译
              </button>
            </template>
          </EmptyState>
        </SectionCard>
      </div>

      <div>
        <SectionCard title="如何积累术语">
          <p class="note-text">
            每一次翻译与术语录入都会沉淀到你的术语库；术语命中情况可在译文预览与合规预检中查看。
          </p>
        </SectionCard>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import SectionCard from './ui/SectionCard.vue'
import EmptyState from './ui/EmptyState.vue'

/**
 * 术语反哺（原型 p4 / O3）。
 *
 * 交付形态：静态骨架 + 两个可由现有接口取到的真实数字。
 * 候选列表留空并说明原因——FR-09 的候选抽取后端完全未实现
 * （occurrences / suggested_foreign / candidate_term / freq 全库 grep 零命中）。
 */
export default {
  name: 'TermFeedback',
  components: { SectionCard, EmptyState },
  props: {
    t: { type: Object, default: () => ({}) },
    changeRoute: { type: Function, default: () => {} }
  },
  setup(props) {
    const historyTotal = ref(null)
    const glossaryTotal = ref(null)

    const ratioText = computed(() => {
      const h = historyTotal.value
      const g = glossaryTotal.value
      if (!h || g === null) return '—'
      if (g === 0) return `0 / ${h}`
      return `1 : ${Math.round(h / g)}`
    })

    onMounted(async () => {
      try {
        const { translateApi, glossaryApi } = await import('../api/index.js')
        const [history, glossary] = await Promise.allSettled([
          // 分页参数是 page/size（不是 per_page），只取 1 条用于读 total
          translateApi.getHistory({ page: 1, size: 1 }),
          glossaryApi.getList({})
        ])

        if (history.status === 'fulfilled') {
          const total = history.value?.data?.data?.total
          if (typeof total === 'number') historyTotal.value = total
        }
        if (glossary.status === 'fulfilled') {
          const list = glossary.value?.data?.data
          if (Array.isArray(list)) glossaryTotal.value = list.length
        }
      } catch (error) {
        console.error('[TermFeedback] 读取统计失败', error)
      }
    })

    return {
      historyTotal,
      glossaryTotal,
      ratioText,
      changeRoute: (...args) => props.changeRoute(...args)
    }
  }
}
</script>

<style scoped>
.note-text {
  font-size: 12px;
  color: var(--ink-2);
  line-height: 1.8;
}

.note-text code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  background: var(--bg-soft);
  border: 1px solid var(--line);
  border-radius: 3px;
  padding: 0 3px;
}
</style>
