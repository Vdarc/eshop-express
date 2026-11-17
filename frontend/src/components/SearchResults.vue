<template>
  <div class="search-results-page">
    <!-- 买家工具条：搜索胶囊 + 界面语言（切换不请求，13 语种字段首次响应已带全） -->
    <div class="ui-buyer-bar">
      <div class="ui-bsearch">
        <span class="ic" aria-hidden="true">⌕</span>
        <input
          ref="searchInput"
          v-model="keyword"
          type="text"
          :placeholder="t.recommend.searchPlaceholder"
          :aria-label="T('search')"
          @keyup.enter="applySearch(1)"
        />
        <button class="go" type="button" @click="applySearch(1)">{{ T('search') }}</button>
      </div>
      <LangSwitch :model-value="viewerLang" @change="onLangChange" />
    </div>

    <!-- 筛选条：品类 chip。不带条数——后端没有品类聚合接口，写死就是假数据 -->
    <div class="ui-filterbar boxed" style="margin-top: 12px">
      <button
        type="button"
        class="ui-fchip"
        :class="{ on: category === 'all' }"
        @click="pickCategory('all')"
      >
        {{ T('allCategories') }}
      </button>
      <button
        v-for="c in CATEGORY_OPTIONS"
        :key="c"
        type="button"
        class="ui-fchip"
        :class="{ on: normalizeCategory(category) === c }"
        @click="pickCategory(c)"
      >
        {{ categoryLabel(c, (t && t.categories) || {}) }}
      </button>
      <span class="result-summary">
        {{ T('totalPrefix') }}
        <b>{{ total }}</b>
        {{ T('totalItems') }}
        <template v-if="keyword.trim()"> · 「{{ keyword.trim() }}」</template>
      </span>
    </div>

    <!-- 降级说明：只在确实有商品回退语种时出现 -->
    <p v-if="!loading && hasFallback" class="ui-sub" style="margin: 10px 0 0">
      ⓘ {{ T('fallbackHint') }}
    </p>

    <main style="margin-top: 14px">
      <div v-if="loading" class="ui-note">{{ T('loading') }}</div>

      <!-- 空结果三态：成因不同，文案与动作就不同；条数来自二次查询 -->
      <div v-else-if="products.length === 0" class="ui-empty3">
        <EmptyState
          v-for="state in emptyStates"
          :key="state.title"
          :icon="state.icon"
          :title="state.title"
          :desc="state.desc"
        >
          <template #actions>
            <button type="button" class="ui-btn ui-btn-sm ui-btn-sec" @click="runEmptyAction(state.kind)">
              {{ state.action }}
            </button>
          </template>
        </EmptyState>
      </div>

      <div v-else class="ui-rgrid">
        <ProductCard
          v-for="product in products"
          :key="product.esin"
          :product="product"
          :viewer-lang="viewerLang"
          @open="navigateToProduct"
        />
      </div>

      <div v-if="!loading && pages > 1" class="ui-pager">
        <button
          type="button"
          class="ui-btn ui-btn-sm ui-btn-sec"
          :disabled="currentPage <= 1"
          @click="applySearch(currentPage - 1)"
        >
          {{ T('prevPage') }}
        </button>
        <span><b class="cur">{{ currentPage }}</b> / {{ pages }}</span>
        <button
          type="button"
          class="ui-btn ui-btn-sm ui-btn-sec"
          :disabled="currentPage >= pages"
          @click="applySearch(currentPage + 1)"
        >
          {{ T('nextPage') }}
        </button>
      </div>
    </main>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import LangSwitch from './ui/LangSwitch.vue'
import ProductCard from './ui/ProductCard.vue'
import EmptyState from './ui/EmptyState.vue'
import {
  categoryLabel,
  categoryRequestValue,
  normalizeCategory,
  CATEGORY_OPTIONS,
  resolveDisplay
} from '../utils/product.js'
import { makeT } from '../i18n/ui.js'

const PAGE_SIZE = 20

/**
 * 买家搜索结果页（原型 p6 + p7）。
 *
 * 相对原实现的四处改正：
 * 1. 「共 N 件」读后端 total，不再用本页数组长度（翻页后必然错）
 * 2. 翻页替换结果，不再 concat 追加（原实现越翻越长）
 * 3. 渲染后端已返回的 <em> 高亮（原实现 grep highlight 零命中，买家看不出为何命中）
 * 4. 空结果按成因分三态，条数来自二次查询而不是写死
 *
 * 一处刻意不传的参数：country。它在后端同时是「国家过滤」和「结算币种」，
 * 传买家国家会把结果静默砍到该国商品（库里 2383 条几乎都不是沙特）——
 * 表现为「搜不到也不报错」。币种换算交给详情页，那里没有过滤副作用。
 */
export default {
  name: 'SearchResults',
  components: { LangSwitch, ProductCard, EmptyState },
  props: {
    t: { type: Object, default: () => ({}) },
    changeRoute: { type: Function, default: () => {} },
    currentViewParams: { type: Object, default: () => ({}) },
    lang: { type: String, default: 'zh' }
  },
  emits: ['update:lang'],
  setup(props, { emit }) {
    const keyword = ref('')
    const category = ref('all')
    const products = ref([])
    const loading = ref(false)
    const total = ref(0)
    const pages = ref(0)
    const currentPage = ref(1)
    const searchInput = ref(null)

    // 空结果探测出来的两个真实条数
    const noCatTotal = ref(null)
    const noKwTotal = ref(null)
    const probing = ref(false)

    const viewerLang = computed(() => props.lang || 'zh')
    const T = (key, vars) => makeT(viewerLang.value)(key, vars)

    const hasFallback = computed(() =>
      products.value.some((p) => resolveDisplay(p, viewerLang.value).isFallback)
    )

    const onLangChange = (code) => emit('update:lang', code)

    /** 统一的列表请求。page/per_page 与后端 1..100 的夹取保持一致。 */
    const fetchList = async ({ page = 1, kw = '', cat = 'all' } = {}) => {
      const { productApi } = await import('../api/index.js')
      const params = {
        page,
        per_page: PAGE_SIZE,
        language: viewerLang.value
      }
      const search = String(kw || '').trim()
      if (search) params.search = search
      // 品类发规范化值（3C 大写），否则 ES 的 keyword term 查询匹配不到
      const catValue = categoryRequestValue(cat)
      if (catValue) params.category = catValue
      return productApi.getList(params)
    }

    const applySearch = async (page = 1) => {
      loading.value = true
      noCatTotal.value = null
      noKwTotal.value = null
      try {
        const res = await fetchList({ page, kw: keyword.value, cat: category.value })
        const data = res?.data || {}
        products.value = Array.isArray(data.products) ? data.products : []
        total.value = Number(data.total || 0)
        pages.value = Number(data.pages || 0)
        currentPage.value = Number(data.current || page)
        // 记住搜索上下文，详情页「返回搜索结果」要用
        localStorage.setItem(
          'lastSearchState',
          JSON.stringify({ q: keyword.value.trim(), category: category.value })
        )
        if (total.value === 0) await probeEmpty()
      } catch (error) {
        console.error('[SearchResults] 搜索失败', error)
        products.value = []
        total.value = 0
        pages.value = 0
      } finally {
        loading.value = false
      }
    }

    /**
     * 空结果时补两个条数，用于区分「关键词太窄」和「组合过窄」。
     * 只取 total（per_page=1），不取列表。
     */
    const probeEmpty = async () => {
      probing.value = true
      const jobs = []
      if (category.value !== 'all') {
        jobs.push(
          fetchList({ page: 1, kw: keyword.value, cat: 'all' })
            .then((r) => Number(r?.data?.total || 0))
            .catch(() => null)
            .then((n) => (noCatTotal.value = n))
        )
      }
      if (keyword.value.trim()) {
        jobs.push(
          fetchList({ page: 1, kw: '', cat: category.value })
            .then((r) => Number(r?.data?.total || 0))
            .catch(() => null)
            .then((n) => (noKwTotal.value = n))
        )
      }
      await Promise.allSettled(jobs)
      probing.value = false
    }

    const pickCategory = (c) => {
      category.value = c
      applySearch(1)
    }

    const emptyStates = computed(() => {
      const kw = keyword.value.trim()
      const cat = category.value
      const catName = categoryLabel(cat, (props.t && props.t.categories) || {})
      const list = []

      if (kw && cat !== 'all') {
        if (noKwTotal.value) {
          list.push({
            kind: 'clearKeyword',
            icon: '⌕',
            title: T('categoryHasStock'),
            desc: T('categoryHasStockDesc', { kw, cat: catName, n: noKwTotal.value }),
            action: T('viewCategoryAll')
          })
        }
        if (noCatTotal.value) {
          list.push({
            kind: 'clearCategory',
            icon: '◌',
            title: T('comboTooNarrow'),
            desc: T('comboTooNarrowDesc', { n: noCatTotal.value }),
            action: T('dropCategory')
          })
        }
      } else if (kw) {
        list.push({
          kind: 'focusKeyword',
          icon: '⌕',
          title: T('noKeywordResult'),
          desc: T('noKeywordResult') + '：' + kw,
          action: T('tryAnotherWord')
        })
      } else if (cat !== 'all') {
        list.push({
          kind: 'clearAll',
          icon: '▢',
          title: T('categoryEmpty'),
          desc: T('categoryEmptyDesc', { cat: catName }),
          action: T('viewAllProducts')
        })
      } else {
        list.push({
          kind: 'reload',
          icon: '▢',
          title: T('noProducts'),
          desc: T('noProductsDesc'),
          action: T('retry')
        })
      }

      // 兜底：探测还没回来时至少给一个可操作块，避免空白页
      if (list.length === 0) {
        list.push({
          kind: 'reload',
          icon: '◌',
          title: T('noProducts'),
          desc: T('noProductsDesc'),
          action: T('retry')
        })
      }
      return list
    })

    const runEmptyAction = (kind) => {
      if (kind === 'focusKeyword') {
        // 「换个词」只清空并聚焦输入框，不自动重查——否则会顺带把全部商品刷出来，
        // 动作和文案就不是一回事了
        keyword.value = ''
        requestAnimationFrame(() => searchInput.value?.focus())
        return
      }
      if (kind === 'clearKeyword') {
        keyword.value = ''
      } else if (kind === 'clearCategory') {
        category.value = 'all'
      } else if (kind === 'clearAll') {
        keyword.value = ''
        category.value = 'all'
      } else if (kind === 'reload') {
        applySearch(1)
        return
      }
      applySearch(1)
    }

    const navigateToProduct = (esin) => {
      if (!esin) return
      props.changeRoute(`productDetail?esin=${encodeURIComponent(esin)}`)
    }

    onMounted(() => {
      const params = props.currentViewParams || {}
      let q = params.q !== undefined ? params.q : params.search !== undefined ? params.search : ''
      if (typeof q === 'string' && q.startsWith('%')) q = decodeURIComponent(q)
      keyword.value = typeof q === 'string' ? q : ''
      category.value = params.category || 'all'

      // 「全部品类 + 空关键词」是合法的浏览态，必须直接拉列表。
      // 此前这里是 if (关键词非空 || 品类非 all) 才请求，导致首次进入
      // 页面既不请求也不报错，products 保持空数组 → 显示「暂无商品 共 0 件」；
      // 而切到具体品类再切回来会走 fetch 分支，所以又有数据。
      applySearch(1)
    })

    return {
      keyword,
      category,
      products,
      loading,
      total,
      pages,
      currentPage,
      searchInput,
      probing,
      viewerLang,
      T,
      hasFallback,
      onLangChange,
      applySearch,
      pickCategory,
      emptyStates,
      runEmptyAction,
      navigateToProduct,
      categoryLabel,
      normalizeCategory,
      CATEGORY_OPTIONS
    }
  }
}
</script>

<style scoped>
.result-summary {
  margin-inline-start: auto;
  color: var(--ink-3);
}

.result-summary b {
  color: var(--ink);
}

.ui-pager .cur {
  color: var(--ink);
}

.ui-empty3:only-child {
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}
</style>
