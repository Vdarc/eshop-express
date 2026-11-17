<template>
  <div class="product-publish">
    <StepBar :steps="steps" :current="step" />

    <!-- ================= 第 1 步 · 基本信息 ================= -->
    <div v-if="step === 0" class="ui-split" style="margin-top: 18px">
      <div>
        <SectionCard :title="t.publish.basicInfo" hint="源语言：中文">
          <div class="ui-field">
            <label>{{ t.publish.titleLabel }}<span class="req">*</span></label>
            <input v-model.trim="form.title" type="text" :placeholder="t.publish.titlePlaceholder" />
          </div>

          <div class="ui-g2">
            <div class="ui-field">
              <label>{{ t.publish.categoryLabel }}<span class="req">*</span></label>
              <select v-model="form.category">
                <option value="clothes">{{ t.categories.clothes }}</option>
                <option value="3c">{{ t.categories['3c'] }}</option>
                <option value="home">{{ t.categories.home }}</option>
                <option value="beauty">{{ t.categories.beauty }}</option>
                <option value="baby">{{ t.categories.baby }}</option>
                <option value="sports">{{ t.categories.sports }}</option>
                <option value="car">{{ t.categories.car }}</option>
              </select>
            </div>
            <div class="ui-field">
              <label>价格（¥ CNY）</label>
              <input v-model.number="form.price" type="number" min="0" step="0.01" placeholder="0.00" />
            </div>
          </div>

          <div class="ui-field">
            <label>{{ t.publish.descriptionLabel }}<span class="req">*</span></label>
            <textarea v-model.trim="form.description" rows="6" :placeholder="t.publish.descriptionPlaceholder"></textarea>
          </div>

          <div class="ui-field">
            <label>{{ t.publish.keywordsLabel }}</label>
            <input v-model.trim="form.keywords" type="text" :placeholder="t.publish.keywordsPlaceholder" />
          </div>

          <div class="ui-field">
            <label>商品图片（最多 2 张）</label>
            <div class="thumbs">
              <div v-for="(image, index) in form.images" :key="index" class="th-preview">
                <img :src="image.preview || image.url" alt="" />
                <button type="button" class="th-remove" @click="removeImage(index)">×</button>
                <div v-if="image.loading" class="th-loading"><span class="spinner"></span></div>
                <div v-if="image.error" class="th-error">上传失败</div>
              </div>
              <button
                v-if="form.images.length < 2"
                type="button"
                class="th-add"
                @click="triggerImageUpload"
              >
                <input ref="fileInput" type="file" accept="image/*" hidden @change="handleImageUpload" />
                <span>+ 上传</span>
              </button>
            </div>
          </div>
        </SectionCard>
      </div>

      <div>
        <SectionCard title="目标市场" style="margin-bottom: 16px">
          <div class="ui-field">
            <label>{{ t.publish.targetCountryLabel }}<span class="req">*</span></label>
            <select v-model="form.target_country">
              <option v-for="c in countries" :key="c" :value="c">{{ c }}</option>
            </select>
          </div>
          <div class="ui-field">
            <label>{{ t.publish.targetLanguagesLabel }}<span class="req">*</span></label>
            <div class="ui-checks">
              <label
                v-for="l in targetLangs"
                :key="l.code"
                class="ui-check"
                :class="{ on: form.target_languages.includes(l.code) }"
              >
                <input
                  type="checkbox"
                  :value="l.code"
                  :checked="form.target_languages.includes(l.code)"
                  @change="toggleLang(l.code)"
                />
                {{ l.label }}
              </label>
            </div>
            <span class="tip">已选 {{ form.target_languages.length }} 个语种</span>
          </div>
          <div class="ui-field">
            <label>{{ t.publish.targetPlatformsLabel }}<span class="req">*</span></label>
            <div class="ui-checks">
              <label
                v-for="p in platforms"
                :key="p.code"
                class="ui-check"
                :class="{ on: form.target_platforms.includes(p.code) }"
              >
                <input
                  type="checkbox"
                  :value="p.code"
                  :checked="form.target_platforms.includes(p.code)"
                  @change="togglePlatform(p.code)"
                />
                {{ p.label }}
              </label>
            </div>
          </div>
        </SectionCard>
      </div>
    </div>

    <!-- 第 1 步的操作条 -->
    <div v-if="step === 0" class="ui-actions">
      <button type="button" class="ui-btn ui-btn-ghost left" @click="resetForm">{{ T('draft') }}</button>
      <button
        type="button"
        class="ui-btn ui-btn-sec"
        :disabled="!form.description"
        @click="showSource = !showSource"
      >
        {{ showSource ? T('hideOriginal') : T('previewSource') }}
      </button>
      <button
        type="button"
        class="ui-btn ui-btn-pri"
        :disabled="publishing || hasUploadingImage"
        @click="handlePublish"
      >
        <span v-if="publishing" class="spinner" style="border-color: #fff; border-top-color: transparent"></span>
        {{ publishing ? T('generating') : T('generate') }}
      </button>
    </div>

    <!-- 原文预览：只回显本次将提交的字段，不做额外请求 -->
    <div v-if="step === 0 && showSource" class="ui-note" style="margin-top: 12px">
      <b>{{ T('original') }}</b><br />
      标题：{{ form.title || '—' }}<br />
      描述：{{ form.description || '—' }}
    </div>

    <!-- ================= 第 2 步 · 译文预览 ================= -->
    <div v-else-if="step === 1">
      <div class="ui-split-3" style="margin-top: 18px">
        <!-- 左：语种列表（12 个目标语种全列，未生成的标灰，与原型一致） -->
        <div>
          <div class="ui-lang-list">
            <div
              v-for="item in targetLangs"
              :key="item.code"
              class="it"
              :class="{ on: item.code === activeLang, off: !hasTranslation(item.code) }"
              @click="selectLang(item.code)"
            >
              <span class="code">{{ item.code }}</span>
              <span class="nm" :dir="textDir(item.code)">{{ item.label }}</span>
              <StatusTag v-if="!hasTranslation(item.code)" kind="mute" label="未生成" />
              <StatusTag v-else-if="isIdentical(item.code)" kind="mute" label="一致" />
              <StatusTag
                v-else
                :kind="decisionOf(item.code) ? 'ok' : 'warn'"
                :label="decisionOf(item.code) ? '已定' : '待决策'"
              />
            </div>
          </div>
          <p class="ui-sub" style="margin-top: 8px">
            本次生成 {{ langKeys.length }} 个语种，未勾选的标「未生成」
          </p>
        </div>

        <!-- 中：标准版 / 适配版 对照 -->
        <div>
          <div class="ui-compare">
            <div class="hd">
              <div>
                {{ T('standard') }}
              </div>
              <div>
                {{ T('adapted') }}
              </div>
            </div>
            <div class="bd">
              <div :dir="textDir(activeLang)" :style="rtlStyle(activeLang)">
                {{ entry.versionA || '—' }}
              </div>
              <div :dir="textDir(activeLang)" :style="rtlStyle(activeLang)">
                {{ entry.versionB || '—' }}
              </div>
            </div>
          </div>

          <div class="decision-bar">
            <span class="ui-sub">{{ T('translationOfDesc') }}</span>
          </div>

          <!-- 决策：整段采纳。不做字符级 diff —— 阿语/泰语前端分词不可靠，PRD 已否决该做法 -->
          <div v-if="isIdentical(activeLang)" class="ui-diff-card">
            <div class="t">{{ T('sameVersion') }}</div>
            <div class="why">模型本次未给出不同的本地化版本，两段文本完全一致。</div>
          </div>
          <div v-else class="ui-diff-card">
            <div class="t">
              标准版与适配版存在差异
              <StatusTag kind="warn" :label="langName(activeLang)" />
            </div>
            <div class="ops">
              <button
                type="button"
                class="ui-btn ui-btn-sm"
                :class="decisionOf(activeLang) === 'B' ? 'ui-btn-pri' : 'ui-btn-sec'"
                @click="decide(activeLang, 'B')"
              >
                {{ T('adoptAdapted') }}
              </button>
              <button
                type="button"
                class="ui-btn ui-btn-sm"
                :class="decisionOf(activeLang) === 'A' ? 'ui-btn-pri' : 'ui-btn-sec'"
                @click="decide(activeLang, 'A')"
              >
                {{ T('keepStandard') }}
              </button>
            </div>
          </div>

          <div v-if="entry.degraded" class="ui-note" style="margin-top: 12px">
            {{ T('degraded') }}
          </div>
        </div>

        <!-- 右：术语与文化说明 -->
        <div>
          <SectionCard :title="T('glossaryHit')" :hint="glossarySummary(activeLang)" style="margin-bottom: 14px">
            <div v-if="injectedTerms(activeLang).length === 0" class="ui-sub">
              {{ T('notChecked') }}
            </div>
            <div v-else>
              <div v-for="term in injectedTerms(activeLang)" :key="term" class="cand-row">
                <span>{{ term }}</span>
                <StatusTag
                  :kind="violatedTerms(activeLang).includes(term) ? 'warn' : 'ok'"
                  :label="violatedTerms(activeLang).includes(term) ? '未落地' : '已用'"
                />
              </div>
            </div>
          </SectionCard>

          <SectionCard :title="T('culturalNotes')" :hint="T('culturalNotesHint')">
            <p class="note-text">{{ entry.cultural_notes || T('noCulturalNotes') }}</p>
          </SectionCard>
        </div>
      </div>

      <div class="ui-actions">
        <button type="button" class="ui-btn ui-btn-ghost left" @click="step = 0">← 上一步</button>
        <button
          type="button"
          class="ui-btn ui-btn-sec"
          :title="T('adoptAllHint')"
          @click="adoptAll('B')"
        >{{ T('adoptAll') }}</button>
        <button
          type="button"
          class="ui-btn ui-btn-sec"
          :title="T('keepAllHint')"
          @click="adoptAll('A')"
        >{{ T('keepAll') }}</button>
        <span class="ui-sub" style="align-self: center">
          {{ T('decided') }} {{ decidedCount }}/{{ langKeys.length }}
          {{ T('langsUnit') }} · {{ T('charsUnit') }} {{ finalChars }}/{{ charLimit }}
        </span>
        <button type="button" class="ui-btn ui-btn-pri" @click="step = 2">{{ T('nextStep') }}</button>
      </div>
    </div>

    <!-- ================= 第 3 步 · 合规预检 ================= -->
    <div v-else-if="step === 2">
      <div class="ui-split-l" style="margin-top: 18px">
        <div>
          <!-- 落地率：只有 checked>0 才有意义，否则显示 — -->
          <SectionCard style="margin-bottom: 16px">
            <div class="rate-row">
              <div>
                <div class="ui-big-num">{{ landingRate === null ? '—' : landingRate + '%' }}</div>
                <div class="ui-sub">{{ T('landingRate') }}</div>
              </div>
              <div class="rate-right">
                <div class="rate-count">
                  {{ checkedCount === 0 ? T('notChecked') : `${landedCount} / ${checkedCount} 条落地` }}
                </div>
                <div class="ui-sub">{{ T('landingScope') }}（{{ langName(primaryLang) }}）</div>
              </div>
            </div>
            <div class="ui-bar" style="margin-top: 11px">
              <i :style="{ width: (landingRate || 0) + '%' }"></i>
            </div>
          </SectionCard>

          <div class="section-head">
            <div class="title">{{ T('violations') }} · {{ totalViolations }} 条</div>
            <div class="ops">
              <button
                v-for="lang in langsWithViolation"
                :key="lang"
                type="button"
                class="ui-btn ui-btn-sm"
                :class="filterLang === lang ? 'ui-btn-pri' : 'ui-btn-sec'"
                @click="filterLang = filterLang === lang ? '' : lang"
              >
                {{ langName(lang) }}
              </button>
            </div>
          </div>

          <div v-if="visibleViolations.length === 0">
            <div class="ui-vio">
              <div class="top-line">
                <span class="term">{{ T('noViolation') }}</span>
                <StatusTag kind="ok" :label="T('passed')" />
              </div>
              <div class="bad">{{ T('noViolationDesc') }}</div>
            </div>
          </div>

          <div v-for="item in visibleViolations" :key="item.lang + item.term" class="ui-vio">
            <div class="top-line">
              <span class="term">{{ item.term }}</span>
              <StatusTag kind="warn" label="强制术语" />
              <StatusTag kind="mute" :label="langName(item.lang)" />
            </div>
            <div class="bad">
              该语种译文中未检测到术语库指定译法。
            </div>
            <div class="good">
              请在术语库核对指定译法后修改译文。
            </div>
            <div class="act">
              <button type="button" class="ui-btn ui-btn-sm ui-btn-sec" @click="changeRoute('glossary')">
                去术语库核对译法
              </button>
              <button type="button" class="ui-btn ui-btn-sm ui-btn-ghost" @click="ignoreViolation(item)">
                标记为已知
              </button>
            </div>
          </div>

          <div v-if="totalViolations > 0" class="ui-note" style="margin-top: 14px">
            {{ T('unresolvedHint') }}
          </div>
        </div>

        <!-- 右：平台预检 -->
        <div>
          <SectionCard :title="T('platformCheck')" tight style="margin-bottom: 14px">
            <div v-for="(item, key) in compliance" :key="key" class="platform-row">
              <div class="platform-head">
                <b>{{ platformLabel(key) }}</b>
                <StatusTag :kind="item.passed ? 'ok' : 'warn'" :label="item.passed ? T('passed') : T('failed')" />
              </div>
              <div class="ui-sub">{{ platformDesc(item) }}</div>
              <div v-if="item.messages && item.messages.length" class="platform-msg">
                {{ item.messages.join('；') }}
              </div>
            </div>
          </SectionCard>

          <div class="ui-note">
            <b>预检范围</b><br />
            仅对<b>主语言（{{ langName(primaryLang) }}）的标准版译文</b>校验，{{ T('landingCaveat') }}
          </div>
        </div>
      </div>

      <div class="ui-actions">
        <button type="button" class="ui-btn ui-btn-ghost left" @click="step = 1">← 上一步</button>
        <span class="ui-sub" style="align-self: center">
          {{ totalViolations ? `还有 ${totalViolations} 条术语待人工确认` : '无待处理违规' }}
        </span>
        <button type="button" class="ui-btn ui-btn-pri" @click="step = 3">{{ T('stepDone') }} →</button>
      </div>
    </div>

    <!-- ================= 第 4 步 · 完成 ================= -->
    <div v-else>
      <SectionCard style="margin-top: 18px; margin-bottom: 16px">
        <div class="ui-done-hero">
          <div class="ring">✓</div>
          <h2>{{ T('doneTitle', { n: langKeys.length }) }}</h2>
          <p>{{ T('doneSub') }}</p>
        </div>
        <div class="ui-stats">
          <div>
            <div class="v">{{ langKeys.length }}</div>
            <div class="k">{{ T('targetLangs') }}</div>
          </div>
          <div>
            <div class="v">{{ landingRate === null ? '—' : landingRate + '%' }}</div>
            <div class="k">{{ T('landingRate') }}（{{ langName(primaryLang) }}）</div>
          </div>
          <div>
            <div class="v">{{ totalViolations }}</div>
            <div class="k">{{ T('pendingItems') }}</div>
          </div>
        </div>
      </SectionCard>

      <SectionCard :title="'各语种译文'" hint="仅描述译文">
        <table class="ui-table">
          <thead>
            <tr>
              <th style="width: 150px">语言</th>
              <th>决策</th>
              <th>术语落地</th>
              <th>平台预检</th>
              <th style="width: 90px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in targetLangs" :key="item.code">
              <td>
                <b :dir="textDir(item.code)">{{ item.label }}</b>
                <span class="ui-sub"> {{ item.code }}</span>
              </td>
              <td>
                <StatusTag
                  v-if="hasTranslation(item.code)"
                  :kind="decisionOf(item.code) === 'B' ? 'brand' : 'mute'"
                  :label="decisionOf(item.code) === 'B' ? '适配版' : decisionOf(item.code) === 'A' ? '标准版' : '未决策'"
                />
                <span v-else class="ui-sub">—（未生成）</span>
              </td>
              <td>{{ rateOf(item.code) === null ? '—' : rateOf(item.code) + '%' }}</td>
              <td>
                <StatusTag
                  v-if="item.code === primaryLang && primaryCompliancePassed !== null"
                  :kind="primaryCompliancePassed ? 'ok' : 'warn'"
                  :label="primaryCompliancePassed ? '全部通过' : '有未通过'"
                />
                <span v-else class="ui-sub">—（未预检）</span>
              </td>
              <td>
                <button
                  type="button"
                  class="ui-btn ui-btn-sm ui-btn-sec"
                  :disabled="!hasTranslation(item.code)"
                  @click="selectLang(item.code); step = 1"
                >
                  查看
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </SectionCard>

      <div class="ui-actions">
        <button type="button" class="ui-btn ui-btn-ghost left" @click="resetForm">{{ T('publishAnother') }}</button>
        <button type="button" class="ui-btn ui-btn-sec" @click="step = 2">{{ T('backToCompliance') }}</button>
        <button
          type="button"
          class="ui-btn ui-btn-pri"
          :disabled="!result?.product_id"
          @click="changeRoute(`productDetail?esin=${encodeURIComponent(result.product_id)}`)"
        >
          {{ T('viewProduct') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, reactive } from 'vue'
import StepBar from './ui/StepBar.vue'
import SectionCard from './ui/SectionCard.vue'
import StatusTag from './ui/StatusTag.vue'
import { ALL_LANGS, LANG_NATIVE, textDir } from '../utils/product.js'
import { makeT } from '../i18n/ui.js'

/** 12 个目标语种（不含中文源语言）——这是「生成几次翻译」，不是「存了多少列」 */
const TARGET_LANGS = ALL_LANGS.filter((l) => l !== 'zh').map((code) => ({
  code,
  label: LANG_NATIVE[code]
}))

const PLATFORMS = [
  { code: 'amazon', label: 'Amazon' },
  { code: 'ebay', label: 'eBay' },
  { code: 'aliexpress', label: 'AliExpress' }
]

const COUNTRIES = [
  '沙特', '阿联酋', '美国', '英国', '法国', '德国', '日本', '韩国',
  '俄罗斯', '泰国', '越南', '马来西亚', '印度尼西亚', '菲律宾', '新加坡'
]

/** PublishSupport.MAX_LENGTH */
const CHAR_LIMIT = 512
/** 后端未知平台返回 Integer.MAX_VALUE，前端要格式化成「无词表」而不是印出一个天文数字 */
const NO_LIMIT = 2147483647

/**
 * 商品发布（原型 p1 → p2 → p3 → p5，四步向导）。
 *
 * 数据来源全部是 POST /api/product/publish 的真实响应：
 *   data.product_id                 → ESIN（不是数据库主键）
 *   data.translations[lang]         → { versionA, versionB, cultural_notes, glossary_report, degraded }
 *   data.compliance[platform]       → { passed, current, limit, messages }
 *
 * 三处按原型「看起来该有」但数据不存在的东西，这里不做：
 * 1. 逐条差异采纳 —— 后端只返回两段整句，没有 diff_segments；阿语/泰语前端分词不可靠
 * 2. 违规一键替换 —— violations 只有中文术语名，没有位置/片段/期望词形（无 violations_detail）
 * 3. 各语种「待更新/差异数/重译」—— 无该字段，也无重译接口
 */
export default {
  name: 'ProductPublish',
  components: { StepBar, SectionCard, StatusTag },
  props: {
    t: { type: Object, default: () => ({}) },
    loading: { type: Object, default: () => ({}) },
    changeRoute: { type: Function, default: () => {} },
    lang: { type: String, default: 'zh' }
  },
  setup(props) {
    const T = (key, vars) => makeT(props.lang || 'zh')(key, vars)

    const step = ref(0)
    const steps = computed(() => [
      { key: 'basic', label: T('stepBasic') },
      { key: 'translate', label: T('stepTranslate') },
      { key: 'compliance', label: T('stepCompliance') },
      { key: 'done', label: T('stepDone') }
    ])

    const form = reactive({
      title: '',
      description: '',
      category: 'clothes',
      keywords: '',
      price: '',
      target_country: '沙特',
      // 默认两个语种：一次发布会逐个语种调 LLM，全选 12 个会让演示等待过久
      target_languages: ['en', 'ar'],
      target_platforms: ['amazon', 'ebay'],
      images: []
    })

    const result = ref(null)
    const activeLang = ref('')
    const decisions = ref({})
    const filterLang = ref('')
    const ignored = ref([])
    const fileInput = ref(null)
    const showSource = ref(false)

    const publishing = computed(() => Boolean(props.loading && props.loading.publish))
    const hasUploadingImage = computed(() => form.images.some((img) => img.loading))

    const translations = computed(() => result.value?.translations || {})
    const langKeys = computed(() => Object.keys(translations.value))
    const primaryLang = computed(() => langKeys.value[0] || 'en')
    const compliance = computed(() => result.value?.compliance || {})

    const entry = computed(() => translations.value[activeLang.value] || {})

    const langName = (code) => LANG_NATIVE[code] || code
    const rtlStyle = (code) => (textDir(code) === 'rtl' ? { textAlign: 'right' } : {})
    const platformLabel = (key) => (PLATFORMS.find((p) => p.code === key) || { label: key }).label

    const toggleLang = (code) => {
      const list = form.target_languages
      const i = list.indexOf(code)
      if (i >= 0) list.splice(i, 1)
      else list.push(code)
    }

    const togglePlatform = (code) => {
      const list = form.target_platforms
      const i = list.indexOf(code)
      if (i >= 0) list.splice(i, 1)
      else list.push(code)
    }

    // ---------- 译文决策 ----------
    const versionText = (lang, which) => {
      const e = translations.value[lang] || {}
      const raw = which === 'B' ? e.versionB : e.versionA
      // 后端已把译文规范化为纯文本；这里再兜一层：
      // 若碰到 {translation, notes} 形态的对象，直接 String() 会得到 "[object Object]"，
      // 导致两个不同版本被误判为"一致"。
      if (raw && typeof raw === 'object') {
        return String(raw.translation || raw.text || raw.content || '').trim()
      }
      return String(raw || '').trim()
    }

    const isIdentical = (lang) => versionText(lang, 'A') === versionText(lang, 'B')

    // 该语种本次是否真的生成了译文（列表里 12 项全列，未生成的为只读状态）
    const hasTranslation = (lang) => Boolean(translations.value[lang])

    const selectLang = (lang) => {
      if (!hasTranslation(lang)) return
      activeLang.value = lang
    }

    const decisionOf = (lang) => decisions.value[lang] || null

    const decide = (lang, which) => {
      decisions.value = { ...decisions.value, [lang]: which }
    }

    const adoptAll = (which) => {
      const next = {}
      for (const lang of langKeys.value) next[lang] = which
      decisions.value = next
    }

    const decidedCount = computed(
      () =>
        langKeys.value.filter((l) => decisions.value[l] || isIdentical(l)).length
    )

    // 终稿字数：主语言决策版本的长度（平台限制按 512 字符）
    const finalChars = computed(() => {
      const lang = activeLang.value || primaryLang.value
      const which = decisions.value[lang] || 'A'
      return versionText(lang, which).length
    })

    // ---------- 术语 ----------
    const reportOf = (lang) => translations.value[lang]?.glossary_report || null
    const injectedTerms = (lang) => reportOf(lang)?.terms_injected || []
    const violatedTerms = (lang) => reportOf(lang)?.violations || []

    const glossarySummary = (lang) => {
      const r = reportOf(lang)
      if (!r || !r.checked) return T('notChecked')
      const bad = (r.violations || []).length
      return `${r.checked - bad} / ${r.checked}`
    }

    const checkedCount = computed(() => reportOf(primaryLang.value)?.checked || 0)
    const landedCount = computed(() => {
      const r = reportOf(primaryLang.value)
      if (!r) return 0
      return Math.max(0, (r.checked || 0) - (r.violations || []).length)
    })

    // checked === 0 时 compliance_rate 恒为 1.0，直接乘 100 会显示「100%」——必须显示 —
    const landingRate = computed(() => {
      const r = reportOf(primaryLang.value)
      if (!r || !r.checked) return null
      return Math.round((r.compliance_rate ?? 0) * 100)
    })

    const rateOf = (lang) => {
      const r = reportOf(lang)
      if (!r || !r.checked) return null
      return Math.round((r.compliance_rate ?? 0) * 100)
    }

    const allViolations = computed(() => {
      const list = []
      for (const lang of langKeys.value) {
        const r = reportOf(lang)
        if (!r) continue
        for (const term of r.violations || []) {
          if (ignored.value.includes(`${lang}:${term}`)) continue
          list.push({ lang, term, checkedOn: r.checked_on || 'versionA' })
        }
      }
      return list
    })

    const totalViolations = computed(() => allViolations.value.length)
    const langsWithViolation = computed(() => [...new Set(allViolations.value.map((v) => v.lang))])
    const visibleViolations = computed(() =>
      filterLang.value ? allViolations.value.filter((v) => v.lang === filterLang.value) : allViolations.value
    )

    const ignoreViolation = (item) => {
      ignored.value = [...ignored.value, `${item.lang}:${item.term}`]
    }

    // ---------- 平台预检 ----------
    const platformDesc = (item) => {
      if (!item) return ''
      if (item.limit === NO_LIMIT) return T('noPlatformRule')
      return `禁用词 ${(item.messages || []).length ? '有命中' : '0'} · 字符 ${item.current} / ${item.limit}`
    }

    const primaryCompliancePassed = computed(() => {
      const keys = Object.keys(compliance.value)
      if (!keys.length) return null
      return keys.every((k) => compliance.value[k]?.passed)
    })

    // ---------- 图片上传 ----------
    const triggerImageUpload = () => fileInput.value?.click()

    const handleImageUpload = async (event) => {
      const file = event.target.files?.[0]
      event.target.value = ''
      if (!file) return

      const previewUrl = URL.createObjectURL(file)
      const index = form.images.length
      form.images.push({ preview: previewUrl, loading: true, error: false, url: null })

      try {
        const { productApi } = await import('../api/index.js')
        const response = await productApi.uploadImage(file)
        if (response?.data?.code === 200) {
          form.images[index].url = response.data.data.image_url
          form.images[index].loading = false
        } else {
          throw new Error(response?.data?.msg || response?.data?.error || '上传失败')
        }
      } catch (error) {
        console.error('[ProductPublish] 图片上传失败', error)
        form.images[index].loading = false
        form.images[index].error = true
        URL.revokeObjectURL(previewUrl)
      }
    }

    const removeImage = (index) => form.images.splice(index, 1)

    // ---------- 发布 ----------
    const validate = () => {
      if (!form.title) return '请填写商品标题'
      if (!form.description) return '请填写商品描述'
      if (form.target_languages.length === 0) return '请至少选择一个目标语言'
      if (form.target_platforms.length === 0) return '请至少选择一个目标平台'
      return ''
    }

    const handlePublish = async () => {
      const msg = validate()
      if (msg) {
        alert(msg)
        return
      }
      if (props.loading) props.loading.publish = true
      try {
        const { productApi } = await import('../api/index.js')
        const payload = {
          title: form.title,
          description: form.description,
          category: form.category,
          keywords: form.keywords,
          price: form.price === '' ? 0 : Number(form.price),
          target_country: form.target_country,
          target_languages: [...form.target_languages],
          target_platforms: [...form.target_platforms],
          images: form.images.filter((img) => img.url).map((img) => img.url)
        }
        const response = await productApi.publish(payload)
        if (response?.data?.code === 200) {
          result.value = response.data.data
          const keys = Object.keys(result.value.translations || {})
          activeLang.value = keys[0] || ''
          // 两版一致的语种视为已定，避免「已定 0/12」这种假待办
          const initial = {}
          for (const k of keys) {
            const e = result.value.translations[k] || {}
            if (String(e.versionA || '').trim() === String(e.versionB || '').trim()) initial[k] = 'A'
          }
          decisions.value = initial
          step.value = 1
        } else {
          alert('发布失败：' + (response?.data?.msg || response?.data?.error || '未知错误'))
        }
      } catch (error) {
        console.error('[ProductPublish] 发布失败', error)
        // BusinessException 走 HTTP 4xx/5xx，axios 进 reject，后端的中文原因在 error.response.data.msg
        const msg = error?.response?.data?.msg || error?.message || '未知错误'
        alert('发布失败：' + msg)
      } finally {
        if (props.loading) props.loading.publish = false
      }
    }

    const resetForm = () => {
      form.title = ''
      form.description = ''
      form.category = 'clothes'
      form.keywords = ''
      form.price = ''
      form.target_country = '沙特'
      form.target_languages = ['en', 'ar']
      form.target_platforms = ['amazon', 'ebay']
      form.images = []
      result.value = null
      decisions.value = {}
      ignored.value = []
      filterLang.value = ''
      activeLang.value = ''
      step.value = 0
    }

    return {
      step,
      steps,
      form,
      result,
      activeLang,
      decisions,
      filterLang,
      fileInput,
      showSource,
      publishing,
      hasUploadingImage,
      translations,
      langKeys,
      primaryLang,
      compliance,
      entry,
      targetLangs: TARGET_LANGS,
      platforms: PLATFORMS,
      countries: COUNTRIES,
      charLimit: CHAR_LIMIT,
      T,
      langName,
      textDir,
      rtlStyle,
      platformLabel,
      platformDesc,
      toggleLang,
      togglePlatform,
      isIdentical,
      hasTranslation,
      selectLang,
      decisionOf,
      decide,
      adoptAll,
      decidedCount,
      finalChars,
      injectedTerms,
      violatedTerms,
      glossarySummary,
      landingRate,
      rateOf,
      checkedCount,
      landedCount,
      visibleViolations,
      totalViolations,
      langsWithViolation,
      ignoreViolation,
      primaryCompliancePassed,
      triggerImageUpload,
      handleImageUpload,
      removeImage,
      handlePublish,
      resetForm,
      changeRoute: (...args) => props.changeRoute(...args)
    }
  }
}
</script>

<style scoped>
/* 步骤条落在内容区里，用描边卡片包裹，避免与顶栏形成两条通栏横线 */
.product-publish :deep(.ui-steps) {
  border: 1px solid var(--line);
  border-radius: var(--r);
  background: #fff;
}

/* 未生成的语种：可看不可选 */
.product-publish :deep(.ui-lang-list .it.off) {
  opacity: 0.55;
  cursor: default;
}

.product-publish :deep(.ui-lang-list .it.off:hover) {
  background: transparent;
}

/* 第 1 步操作条：吸底成一条工具条，长表单滚动时主操作始终可见 */
.product-publish > .ui-actions {
  position: sticky;
  bottom: 0;
  z-index: 5;
  margin-top: 16px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-top: 1px solid var(--line);
  border-radius: var(--r);
  background: rgba(255, 255, 255, .94);
  backdrop-filter: saturate(180%) blur(8px);
  box-shadow: 0 -6px 18px -12px rgba(16, 24, 40, .28);
}

.thumbs {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
}

.th-preview,
.th-add {
  width: 88px;
  height: 88px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line);
  overflow: hidden;
  position: relative;
  background: var(--bg-soft);
}

.th-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.th-add {
  border: 1.5px dashed var(--line-3);
  background: var(--bg-soft);
  color: var(--brand);
  font-size: 11.5px;
  font-weight: 500;
  display: grid;
  place-items: center;
  gap: 2px;
  transition: border-color var(--t-fast) var(--ease-out),
    background-color var(--t-fast) var(--ease-out),
    transform var(--t-fast) var(--ease-out);
}

.th-add:hover {
  border-color: var(--brand);
  background: var(--brand-soft);
  transform: translateY(-1px);
}

.th-remove {
  position: absolute;
  top: 3px;
  inset-inline-end: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(20, 24, 33, 0.55);
  color: #fff;
  font-size: 12px;
  display: grid;
  place-items: center;
  line-height: 1;
}

.th-loading {
  position: absolute;
  inset: 0;
  background: rgba(20, 24, 33, 0.45);
  display: grid;
  place-items: center;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #fff;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.th-error {
  position: absolute;
  inset-inline: 0;
  bottom: 0;
  background: rgba(224, 49, 49, 0.85);
  color: #fff;
  font-size: 10px;
  text-align: center;
  padding: 2px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.decision-bar {
  margin-top: 16px;
  margin-bottom: 9px;
}

.cand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line-2);
  font-size: 12.5px;
}

.cand-row:last-child {
  border-bottom: 0;
}

.cand-row span:first-child {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.note-text {
  font-size: 12px;
  color: var(--ink-2);
  line-height: 1.75;
  white-space: pre-wrap;
}

.rate-row {
  display: flex;
  align-items: flex-end;
  gap: 14px;
  margin-bottom: 11px;
  flex-wrap: wrap;
}

.rate-right {
  margin-inline-start: auto;
  text-align: end;
}

.rate-count {
  font-size: 13px;
  font-weight: 600;
}

.section-head {
  display: flex;
  align-items: center;
  margin-bottom: 11px;
  gap: 10px;
  flex-wrap: wrap;
}

.section-head .title {
  font-size: 13px;
  font-weight: 600;
}

.section-head .ops {
  margin-inline-start: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.platform-row {
  padding: 12px 15px;
  border-bottom: 1px solid var(--line-2);
}

.platform-row:last-child {
  border-bottom: 0;
}

.platform-head {
  display: flex;
  align-items: center;
  margin-bottom: 4px;
}

.platform-head b {
  font-size: 12.5px;
}

.platform-head .ui-tag {
  margin-inline-start: auto;
}

.platform-msg {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--warn);
  line-height: 1.6;
}
</style>
