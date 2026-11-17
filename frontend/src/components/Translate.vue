<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.translate.title }}</div>
        <div class="sub">上传或粘贴商品文案，一次生成「标准版」与「文化适配版」两套译文</div>
      </div>
      <div class="acts">
        <span class="ui-tag ui-tag-brand">术语库约束已注入</span>
      </div>
    </div>

    <!-- 源文本 -->
    <section class="ui-card">
      <header class="ui-card-h">
        <h3>源文本</h3>
        <span class="hint">支持 .txt / .docx，或直接粘贴</span>
      </header>
      <div class="ui-card-b">
        <div class="src-grid">
          <div>
            <label class="field-label">{{ t.translate.sourceFile }}</label>
            <div
              class="upload-area"
              :class="{ disabled: isTextInputting }"
              @dragover.prevent="handleDragOver"
              @dragleave="handleDragLeave"
              @drop.prevent="handleDrop"
            >
              <div class="upload-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor"
                     stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 16V4m0 0L8 8m4-4l4 4" />
                  <path d="M4 15v3.5A1.5 1.5 0 005.5 20h13a1.5 1.5 0 001.5-1.5V15" />
                </svg>
              </div>
              <div class="upload-text">
                {{ t.translate.uploadText }}
                <div class="text-sm mt-1">{{ t.translate.uploadHint }}</div>
              </div>
            </div>
            <div v-if="uploadedFileName" class="file-info">
              <span class="file-name">📄 {{ uploadedFileName }}</span>
              <span class="remove-file-btn" @click="removeUploadedFile">×</span>
            </div>
          </div>
          <div>
            <label class="field-label">{{ t.translate.orText }}</label>
            <textarea
              v-model="form.text"
              rows="8"
              :placeholder="t.translate.placeholder"
              :disabled="false"
              @input="handleTextInput"
            ></textarea>
            <div class="char-count ui-sub">{{ (form.text || '').length }} 字符</div>
          </div>
        </div>
      </div>
    </section>

    <!-- 翻译设置 -->
    <section class="ui-card">
      <header class="ui-card-h">
        <h3>翻译设置</h3>
        <span class="hint">目标语言决定译文语种，类目与国家影响本地化策略</span>
      </header>
      <div class="ui-card-b">
        <div class="opt-grid">
          <div class="ui-field">
            <label>{{ t.translate.targetLang }}</label>
            <select v-model="form.target_lang">
              <option value="en">{{ t.languages.en }}</option>
              <option value="ar">{{ t.languages.ar }}</option>
              <option value="fr">{{ t.languages.fr }}</option>
              <option value="de">{{ t.languages.de }}</option>
              <option value="ja">{{ t.languages.ja }}</option>
              <option value="ko">{{ t.languages.ko }}</option>
              <option value="ru">{{ t.languages.ru }}</option>
              <option value="th">{{ t.languages.th }}</option>
              <option value="vi">{{ t.languages.vi }}</option>
              <option value="ms">{{ t.languages.ms }}</option>
              <option value="id">{{ t.languages.id }}</option>
              <option value="tl">{{ t.languages.tl }}</option>
            </select>
          </div>
          <div class="ui-field">
            <label>{{ t.translate.category }}</label>
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
            <label>{{ t.translate.country }}</label>
            <select v-model="form.country">
              <option value="saudi">{{ t.countries.saudi }}</option>
              <option value="uae">{{ t.countries.uae }}</option>
              <option value="usa">{{ t.countries.usa }}</option>
              <option value="uk">{{ t.countries.uk }}</option>
              <option value="france">{{ t.countries.france }}</option>
              <option value="germany">{{ t.countries.germany }}</option>
              <option value="japan">{{ t.countries.japan }}</option>
              <option value="korea">{{ t.countries.korea }}</option>
              <option value="china">{{ t.countries.china }}</option>
              <option value="russia">{{ t.countries.russia }}</option>
              <option value="thailand">{{ t.countries.thailand }}</option>
              <option value="vietnam">{{ t.countries.vietnam }}</option>
              <option value="malaysia">{{ t.countries.malaysia }}</option>
              <option value="indonesia">{{ t.countries.indonesia }}</option>
              <option value="philippines">{{ t.countries.philippines }}</option>
              <option value="singapore">{{ t.countries.singapore }}</option>
            </select>
          </div>
          <div class="opt-action">
            <button
              @click="translate"
              :disabled="loadingTranslate || !form.text.trim()"
              class="btn btn-primary btn-run"
            >
              <span v-if="loadingTranslate" class="loader"></span>
              {{ loadingTranslate ? t.translate.loading : t.translate.button }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- 结果 -->
    <section v-if="showResults" class="ui-card result-card">
      <header class="ui-card-h">
        <h3>{{ t.translate.results }}</h3>
        <div class="export-buttons">
          <button @click="handleExport('txt')" class="btn btn-secondary">{{ t.export.txt }}</button>
          <button @click="handleExport('excel')" class="btn btn-primary">{{ t.export.excel }}</button>
        </div>
      </header>
      <div class="ui-card-b">
        <!-- 版本A -->
        <div class="version-card" :class="{ selected: selectedVersion === 'A' }" @click="selectVersion('A')">
          <div class="version-title">
            {{ t.translate.versionA }}
            <span class="version-tag tag-direct">{{ t.translate.tagDirect }}</span>
          </div>
          <p>{{ translationResult.versionA }}</p>
        </div>
        <!-- 版本B -->
        <div class="version-card" :class="{ selected: selectedVersion === 'B' }" @click="selectVersion('B')">
          <div class="version-title">
            {{ t.translate.versionB }}
            <span class="version-tag tag-localized">{{ t.translate.tagLocalized }}</span>
          </div>
          <p>{{ translationResult.versionB }}</p>
        </div>

        <!-- 核心卖点 -->
        <div class="fold-card">
          <div class="card-header" @click="toggleCard('selling')">
            <h4 class="card-title">{{ t.translate.coreSellingPoints }}</h4>
            <span class="card-icon" :class="{ open: expandedCards.selling }">⌄</span>
          </div>
          <div class="card-body" v-show="expandedCards.selling">
            <div>
              <span v-for="(point, index) in translationResult.core_selling_points" :key="'sp-'+index" class="selling-tag">{{ point }}</span>
            </div>
          </div>
        </div>

        <!-- 文化敏感点提示 -->
        <div class="cultural-notice">
          <h4>⚠ {{ t.translate.culturalNotes }}</h4>
          <p>{{ translationResult.cultural_notes }}</p>
        </div>

        <!-- SEO关键词建议 -->
        <div class="seo-tags">
          <h4>⌕ {{ t.translate.seoKeywords }}</h4>
          <div>
            <span v-for="(kw, index) in translationResult.seo_keywords" :key="'kw-'+index" class="seo-keyword-tag">{{ kw }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 空态引导：避免结果区缺失造成的突兀留白 -->
    <section v-else class="ui-card empty-hint">
      <div class="ui-card-b">
        <div class="empty-inner">
          <div class="ico">◈</div>
          <div>
            <b>译文会显示在这里</b>
            <p class="ui-sub">填写左侧文案并选择目标语言后点击「开始翻译」，将同时产出标准版与文化适配版。</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { ref } from 'vue'

export default {
  name: 'Translate',
  props: {
    t: Object,
    form: Object,
    loadingTranslate: Boolean,
    showResults: Boolean,
    translationResult: Object,
    selectedVersion: String,
    expandedCards: Object,
    uploadedFileName: String,
    isTextInputting: Boolean,
    translate: Function,
    removeUploadedFile: Function,
    handleTextInput: Function,
    selectVersion: Function,
    toggleCard: Function,
    handleExport: Function
  },
  setup(props, { emit }) {
    const handleDragOver = (event) => {
      event.currentTarget.classList.add('drag-active')
    }
    
    const handleDragLeave = (event) => {
      event.currentTarget.classList.remove('drag-active')
    }
    
    const handleDrop = (event) => {
      event.currentTarget.classList.remove('drag-active')
      const files = event.dataTransfer.files
      if (files && files.length > 0) {
        const file = files[0]
        emit('fileSelected', file)
      }
    }
    
    return {
      handleDragOver,
      handleDragLeave,
      handleDrop
    }
  }
}
</script>
<style scoped>
/* 源文本双栏：左上传、右粘贴；窄屏自动堆叠 */
.src-grid {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 18px;
  align-items: start;
}

.field-label {
  display: block;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ink-2);
  margin-bottom: 6px;
}

.char-count {
  text-align: right;
  margin-top: 5px;
}

/* 设置区：三个下拉 + 主操作按钮同一行，按钮贴底对齐 */
.opt-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  gap: 14px;
  align-items: end;
}

.opt-grid .ui-field {
  margin-bottom: 0;
}

.opt-action {
  padding-bottom: 1px;
}

.btn-run {
  height: 36px;
  min-width: 132px;
}

/* 结果卡：标题栏右侧放导出按钮，去掉原来的二级标题行 */
.result-card .ui-card-h {
  flex-wrap: wrap;
}

.result-card .ui-card-h .export-buttons {
  margin-inline-start: auto;
}

/* 折叠箭头：用旋转代替字符替换，展开/收起有连续感 */
.card-icon {
  transition: transform var(--t-base) var(--ease-out);
}

.card-icon.open {
  transform: rotate(180deg);
}

/* 空态 */
.empty-inner {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 4px;
}

.empty-inner .ico {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 16px;
  flex: none;
}

.empty-inner b {
  font-size: 13px;
  color: var(--ink-2);
  display: block;
  margin-bottom: 3px;
}

@media (max-width: 1024px) {
  .src-grid { grid-template-columns: 1fr; }
  .opt-grid { grid-template-columns: 1fr 1fr; }
  .opt-action { grid-column: 1 / -1; }
  .btn-run { width: 100%; }
}

@media (max-width: 640px) {
  .opt-grid { grid-template-columns: 1fr; }
}
</style>
