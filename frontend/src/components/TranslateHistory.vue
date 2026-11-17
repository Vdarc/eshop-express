<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.translationHistory.title }}</div>
        <div class="sub">按时间倒序保存的历史译文，可展开对照两个版本</div>
      </div>
      <div class="acts">
        <div class="search-box">
          <span aria-hidden="true">⌕</span>
          <input type="text" placeholder="搜索历史记录" />
        </div>
      </div>
    </div>

    <section class="ui-card">
      <header class="ui-card-h">
        <h3>记录列表</h3>
        <span class="hint">最近 20 条</span>
      </header>
      <div class="ui-card-b hist-body">
        <div v-if="historyData.length === 0" class="ui-empty">
          <div class="ico">▤</div>
          <b>{{ t.translationHistory.noRecords }}</b>
          <p>完成一次翻译后，记录会自动出现在这里</p>
        </div>

        <!-- 历史记录卡片 -->
        <article
          v-for="(item, index) in historyData"
          :key="index"
          class="hist-item"
          :class="{ open: expandedCards[index] }"
        >
          <!-- 卡片头部 -->
          <header class="hist-head" @click="toggleExpand(index)">
            <div class="hist-main">
              <h3>{{ item.originalText || "（无原文）" }}</h3>
              <div class="hist-meta">
                <span class="ui-tag ui-tag-brand">{{ t.languages[item.targetLang] || item.targetLang || "—" }}</span>
                <span class="ui-sub">{{ item.timestamp }}</span>
              </div>
            </div>
            <span class="chev" :class="{ open: expandedCards[index] }" aria-hidden="true">⌄</span>
          </header>

          <!-- 展开的翻译结果 -->
          <div v-if="expandedCards[index]" class="hist-detail">
            <!-- 版本A -->
            <div class="version-card">
              <div class="version-title">
                {{ t.translate.versionA }}
                <span class="version-tag tag-direct">{{ t.translate.tagDirect }}</span>
              </div>
              <p>{{ item.versionA }}</p>
            </div>
            <!-- 版本B -->
            <div class="version-card">
              <div class="version-title">
                {{ t.translate.versionB }}
                <span class="version-tag tag-localized">{{ t.translate.tagLocalized }}</span>
              </div>
              <p>{{ item.versionB }}</p>
            </div>
            <!-- 核心卖点 -->
            <div class="fold-card">
              <div class="card-header" @click="toggleCard(index, 'selling')">
                <h4 class="card-title">{{ t.translate.coreSellingPoints }}</h4>
                <span class="card-icon" :class="{ open: item.expandedCards?.selling }">⌄</span>
              </div>
              <div class="card-body" v-show="item.expandedCards?.selling">
                <div>
                  <span v-for="(point, idx) in item.core_selling_points" :key="'sp-'+idx" class="selling-tag">{{ point }}</span>
                </div>
              </div>
            </div>
            <!-- 文化敏感点提示 -->
            <div class="cultural-notice">
              <h4>⚠ {{ t.translate.culturalNotes }}</h4>
              <p>{{ item.cultural_notes }}</p>
            </div>
            <!-- SEO关键词建议 -->
            <div class="seo-tags">
              <h4>⌕ {{ t.translate.seoKeywords }}</h4>
              <div>
                <span v-for="(kw, idx) in item.seo_keywords" :key="'kw-'+idx" class="seo-keyword-tag">{{ kw }}</span>
              </div>
            </div>
          </div>
        </article>
      </div>

      <div class="pager-bar">
        <button class="btn btn-secondary" disabled>{{ t.translationHistory.previousPage }}</button>
        <span class="ui-sub">第 1 页</span>
        <button class="btn btn-secondary" disabled>{{ t.translationHistory.nextPage }}</button>
      </div>
    </section>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'

export default {
  name: 'TranslateHistory',
  props: {
    t: Object
  },
  setup() {
    const expandedCards = ref({})
    const historyData = ref([])
    
    const loadHistory = async () => {
      try {
        const { translateApi } = await import('../api/index.js')
        const userId = localStorage.getItem('loginUserId')
        
        if (!userId) {
          console.error('User ID not found in localStorage')
          return
        }
        
        const response = await translateApi.getHistory({
          user_id: userId,
          page: 1,
          size: 20
        })
        
        if (response.data.code === 200) {
          const data = response.data.data.list
          historyData.value = data.map(item => ({
            originalText: item.source_text,
            targetLang: item.target_lang,
            timestamp: item.create_time,
            versionA: item.version_a,
            versionB: item.version_b,
            core_selling_points: item.core_selling_points || [],
            cultural_notes: item.cultural_notes || '',
            seo_keywords: item.seo_keywords || [],
            expandedCards: {}
          }))
        }
      } catch (error) {
        console.error('Failed to load translation history:', error)
      }
    }
    
    onMounted(() => {
      loadHistory()
    })
    
    const toggleExpand = (index) => {
      expandedCards.value[index] = !expandedCards.value[index]
    }
    
    const toggleCard = (index, cardType) => {
      if (!historyData.value[index].expandedCards) {
        historyData.value[index].expandedCards = {}
      }
      historyData.value[index].expandedCards[cardType] = !historyData.value[index].expandedCards[cardType]
    }
    
    return {
      historyData,
      expandedCards,
      toggleExpand,
      toggleCard
    }
  }
}
</script>

<style scoped>
/* 搜索框：页头右侧，不占内容宽度 */
.search-box {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: #fff;
  color: var(--ink-3);
  font-size: 12.5px;
  transition: border-color var(--t-fast) var(--ease-out), box-shadow var(--t-fast) var(--ease-out);
}

.search-box:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring);
}

.search-box input {
  width: 190px;
  height: 30px;
  min-height: 0;
  border: 0;
  padding: 0;
  background: transparent;
  font-size: 12.5px;
  box-shadow: none;
}

.hist-body {
  display: grid;
  gap: 10px;
}

/* 单条记录：默认只有标题行，展开后露出完整译文 */
.hist-item {
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #fff;
  overflow: hidden;
  transition: border-color var(--t-base) var(--ease-out),
    box-shadow var(--t-base) var(--ease-out);
}

.hist-item:hover {
  border-color: var(--line-3);
  box-shadow: var(--shadow-1);
}

.hist-item.open {
  border-color: var(--brand-200);
  box-shadow: 0 0 0 3px var(--brand-soft);
}

.hist-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  cursor: pointer;
  user-select: none;
}

.hist-main {
  flex: 1;
  min-width: 0;
}

.hist-main h3 {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--ink);
  margin-bottom: 5px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.hist-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.chev {
  flex: none;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--ink-3);
  background: var(--bg-soft);
  font-size: 13px;
  line-height: 1;
  transition: transform var(--t-base) var(--ease-out), background-color var(--t-fast);
}

.hist-head:hover .chev {
  background: var(--bg-sink);
  color: var(--ink);
}

.chev.open {
  transform: rotate(180deg);
}

.hist-detail {
  padding: 4px 16px 16px;
  border-top: 1px solid var(--line-2);
  animation: fold-in var(--t-base) var(--ease-out) both;
}

.hist-detail .version-card {
  margin-top: 12px;
}

.pager-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 12px 18px;
  border-top: 1px solid var(--line-2);
}

@media (max-width: 768px) {
  .search-box input { width: 130px; }
  .hist-detail { padding: 4px 12px 14px; }
}
</style>
