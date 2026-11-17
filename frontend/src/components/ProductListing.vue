<template>
  <div class="product-listing">
    <!-- 搜索胶囊 + 界面语言 -->
    <div class="ui-buyer-bar">
      <div class="ui-bsearch">
        <span class="ic" aria-hidden="true">⌕</span>
        <input
          v-model="searchQuery"
          type="text"
          :placeholder="t.recommend.searchPlaceholder"
          :aria-label="T('search')"
          @keyup.enter="handleSearch"
        />
        <button class="go" type="button" @click="handleSearch">{{ T('search') }}</button>
      </div>
      <LangSwitch :model-value="viewerLang" @change="onLangChange" />
    </div>

    <!-- 品类 chip -->
    <div class="ui-filterbar boxed" style="margin-top: 12px">
      <button
        type="button"
        class="ui-fchip"
        :class="{ on: selectedCategory === 'all' }"
        @click="pickCategory('all')"
      >
        {{ T('allCategories') }}
      </button>
      <button
        v-for="c in CATEGORY_OPTIONS"
        :key="c"
        type="button"
        class="ui-fchip"
        :class="{ on: normalizeCategory(selectedCategory) === c }"
        @click="pickCategory(c)"
      >
        {{ categoryLabel(c, (t && t.categories) || {}) }}
      </button>
    </div>

    <!-- 热门商品：来自点击排行榜，是真实信号 -->
    <section class="block">
      <div class="block-head">
        <h2>{{ t.recommend.hotProducts }}</h2>
        <span class="ui-sub">{{ T('hotFromRank') }}</span>
      </div>
      <div v-if="hotProducts.length" class="ui-rgrid">
        <ProductCard
          v-for="p in hotProducts"
          :key="p.esin"
          :product="p"
          :viewer-lang="viewerLang"
          @open="navigateToProduct"
        />
      </div>
      <div v-else class="ui-note">{{ loading ? T('loading') : T('noProducts') }}</div>
    </section>

    <!-- 同类热销：推荐服务按品类热度算，不含个性化（行为日志无 user_id） -->
    <section class="block">
      <div class="block-head">
        <h2>{{ T('hotInCategory') }}</h2>
        <span class="ui-sub">{{ T('relatedHint') }}</span>
      </div>
      <div v-if="recommendedProducts.length" class="ui-rgrid">
        <ProductCard
          v-for="p in recommendedProducts.slice(0, 4)"
          :key="p.esin"
          :product="p"
          :viewer-lang="viewerLang"
          @open="navigateToProduct"
        />
      </div>
      <div v-else-if="recommendedLoading" class="ui-note">{{ T('loading') }}</div>
      <div v-else class="ui-note">{{ T('relatedEmpty') }}</div>
    </section>

    <!-- 全部商品 -->
    <section class="block">
      <div class="block-head">
        <h2>{{ t.recommend.allProducts }}</h2>
      </div>
      <div v-if="!loading && products.length" class="ui-rgrid">
        <ProductCard
          v-for="p in products"
          :key="p.esin"
          :product="p"
          :viewer-lang="viewerLang"
          @open="navigateToProduct"
        />
      </div>
      <div v-else-if="loading" class="ui-note">{{ T('loading') }}</div>
      <div v-else class="ui-note">{{ T('noProducts') }}</div>

      <!-- 滚动哨兵：挂在内容滚动容器内，IntersectionObserver 才准 -->
      <div ref="sentinel" class="sentinel"></div>
      <div v-if="loadingMore" class="ui-sub" style="text-align: center; padding: 12px">
        {{ T('loading') }}
      </div>
      <div v-else-if="!hasMore && products.length" class="ui-sub" style="text-align: center; padding: 12px">
        {{ T('noMore') }}
      </div>
    </section>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import LangSwitch from './ui/LangSwitch.vue'
import ProductCard from './ui/ProductCard.vue'
import { categoryLabel, categoryRequestValue, normalizeCategory, CATEGORY_OPTIONS } from '../utils/product.js'
import { makeT } from '../i18n/ui.js'

const PAGE_SIZE = 20

/**
 * 买家商品列表页。
 *
 * 相对原实现的改正：
 * 1. 删掉 getMockRating() —— 无评价表时按 esin 字符码造星级，属假数据
 * 2. 热门/推荐不再把商品对象削成 4 个字段，改为保留完整 13 语种字段，切语言才有内容可换
 * 3. 无限滚动改用 IntersectionObserver 挂在内容容器内（原监听 window.scroll，
 *    但实际滚动的是 .content-wrap，触发不到）
 * 4. 品类请求值归一为 `3C`（ES keyword 大小写敏感，传小写必空）
 */
export default {
  name: 'ProductListing',
  components: { LangSwitch, ProductCard },
  props: {
    t: { type: Object, default: () => ({}) },
    changeRoute: { type: Function, default: () => {} },
    lang: { type: String, default: 'zh' }
  },
  emits: ['update:lang'],
  setup(props, { emit }) {
    const products = ref([])
    const hotProducts = ref([])
    const recommendedProducts = ref([])
    const loading = ref(true)
    const recommendedLoading = ref(false)
    const loadingMore = ref(false)
    const page = ref(1)
    const searchQuery = ref('')
    const selectedCategory = ref('all')
    const hasMore = ref(true)
    const sentinel = ref(null)

    let observer = null

    const viewerLang = computed(() => props.lang || 'zh')
    const T = (key, vars) => makeT(viewerLang.value)(key, vars)

    const onLangChange = (code) => emit('update:lang', code)

    const getUserCountry = () => {
      try {
        return JSON.parse(localStorage.getItem('userProfile') || '{}').country || null
      } catch (e) {
        return null
      }
    }

    /** 加载商品（首页/搜索/换品类） */
    const loadProducts = async (isSearch = false) => {
      if (isSearch) {
        page.value = 1
        products.value = []
        hasMore.value = true
      }
      if (!hasMore.value || loadingMore.value) return

      loadingMore.value = true
      try {
        const { productApi } = await import('../api/index.js')
        const params = {
          page: page.value,
          per_page: PAGE_SIZE,
          language: viewerLang.value
        }
        // 不传 country：它在后端同时是过滤与币种，会把结果静默砍到该国商品
        const catValue = categoryRequestValue(selectedCategory.value)
        if (catValue) params.category = catValue
        if (searchQuery.value.trim()) params.search = searchQuery.value.trim()

        const response = await productApi.getList(params)
        const data = response?.data || {}
        const batch = Array.isArray(data.products) ? data.products : []

        products.value = page.value === 1 ? batch : [...products.value, ...batch]
        const totalPages = Number(data.pages || 0)
        hasMore.value = totalPages > page.value && batch.length > 0
        page.value += 1
      } catch (error) {
        console.error('[ProductListing] 加载商品失败', error)
        hasMore.value = false
      } finally {
        loading.value = false
        loadingMore.value = false
      }
    }

    /** 热门：点击排行榜 → 逐个取详情，保留完整字段 */
    const loadHotProducts = async () => {
      try {
        const { statApi, productApi } = await import('../api/index.js')
        const rank = await statApi.getClickRank()
        const top = rank?.data?.data?.top_products || []
        if (!top.length) return

        const detail = await Promise.allSettled(
          top.slice(0, 4).map((item) => productApi.getByEsin(item.product_id, getUserCountry()))
        )
        hotProducts.value = detail
          .filter((r) => r.status === 'fulfilled' && r.value?.data?.data)
          .map((r) => r.value.data.data)
      } catch (error) {
        console.error('[ProductListing] 加载热门商品失败', error)
      }
    }

    /** 同类热销：推荐服务按品类热度算；入参只接受 clothes / 3c，其它值会被后端默认成 clothes */
    const loadRecommendedProducts = async () => {
      recommendedLoading.value = true
      try {
        const { recommendApi, productApi } = await import('../api/index.js')
        const cat = normalizeCategory(selectedCategory.value)
        const safeCategory = cat === '3c' ? '3c' : 'clothes'
        const response = await recommendApi.getRecommend({
          country: getUserCountry() || '沙特',
          category: safeCategory,
          keyword: ''
        })
        const recs = response?.data?.data?.recommendations || []
        if (!recs.length) return

        const detail = await Promise.allSettled(
          recs.slice(0, 4).map((item) => productApi.getByEsin(item.id, getUserCountry()))
        )
        recommendedProducts.value = detail
          .filter((r) => r.status === 'fulfilled' && r.value?.data?.data)
          .map((r) => r.value.data.data)
      } catch (error) {
        console.error('[ProductListing] 加载推荐商品失败', error)
      } finally {
        recommendedLoading.value = false
      }
    }

    const pickCategory = (c) => {
      selectedCategory.value = c
      searchQuery.value = ''
      loadProducts(true)
      loadRecommendedProducts()
    }

    const handleSearch = () => {
      if (!searchQuery.value.trim()) return
      // 有关键词时走搜索结果页，那里有高亮与三态空结果
      props.changeRoute(`searchResults?q=${encodeURIComponent(searchQuery.value)}`)
    }

    const navigateToProduct = async (esin) => {
      if (!esin) return
      localStorage.removeItem('lastSearchState')
      try {
        const { recommendApi } = await import('../api/index.js')
        await recommendApi.reportClick({ product_id: esin })
      } catch (error) {
        console.error('[ProductListing] 点击上报失败', error)
      }
      props.changeRoute(`productDetail?esin=${encodeURIComponent(esin)}`)
    }

    onMounted(() => {
      loadHotProducts()
      loadRecommendedProducts()
      loadProducts()

      // 用哨兵替代 window.scroll：内容区才是滚动容器
      if (typeof IntersectionObserver !== 'undefined' && sentinel.value) {
        observer = new IntersectionObserver(
          (entries) => {
            if (entries.some((e) => e.isIntersecting) && hasMore.value && !loadingMore.value) {
              loadProducts()
            }
          },
          { rootMargin: '200px' }
        )
        observer.observe(sentinel.value)
      }
    })

    onUnmounted(() => {
      if (observer) observer.disconnect()
    })

    return {
      products,
      hotProducts,
      recommendedProducts,
      loading,
      recommendedLoading,
      loadingMore,
      searchQuery,
      selectedCategory,
      hasMore,
      sentinel,
      viewerLang,
      T,
      onLangChange,
      handleSearch,
      pickCategory,
      navigateToProduct,
      categoryLabel,
      normalizeCategory,
      CATEGORY_OPTIONS
    }
  }
}
</script>

<style scoped>
.block {
  margin-top: 22px;
}

.block-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.block-head h2 {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}

.sentinel {
  height: 1px;
}
</style>
