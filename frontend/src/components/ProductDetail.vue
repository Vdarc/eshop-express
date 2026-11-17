<template>
  <div class="product-detail">
    <div v-if="loading" class="ui-note">{{ T('loading') }}</div>

    <template v-else>
      <!-- 面包屑 -->
      <nav class="ui-breadcrumb">
        <a href="#" @click.prevent="changeRoute('productListing')">{{ t.productDetail.home }}</a>
        <span>/</span>
        <a href="#" @click.prevent="goBackToSearch">
          {{ hasSearchState ? t.productDetail.searchResults : (product.category || t.productDetail.product) }}
        </a>
        <span>/</span>
        <span>{{ display.title || product.esin }}</span>
      </nav>

      <div class="ui-dwrap">
        <!-- 图集 -->
        <div>
          <div class="ui-dgal">
            <div class="minis">
              <i
                v-for="(url, index) in gallery"
                :key="index"
                :class="{ on: index === activeImage }"
                @click="activeImage = index"
              >
                <img :src="url" :alt="T('mainImage')" @error="onThumbError(index)" />
              </i>
              <i v-if="gallery.length === 0" class="on"></i>
            </div>
            <div class="main">
              <img v-if="gallery[activeImage]" :src="gallery[activeImage]" :alt="T('mainImage')" />
              <template v-else>
                {{ T('mainImage') }}
                <span class="tagph">{{ T('placeholderImage') }}</span>
              </template>
            </div>
          </div>
          <p v-if="gallery.length <= 1" class="ui-sub" style="margin-top: 10px">
            {{ T('noMoreImages') }}
          </p>
        </div>

        <!-- 主信息 -->
        <div>
          <!-- 语言切换：13 个语种字段首次响应已带全，切换不发请求 -->
          <div class="lang-row">
            <span class="ui-sub">{{ T('language') }}</span>
            <LangSwitch
              :model-value="viewerLang"
              :disabled="disabledLangs"
              @change="onLangChange"
            />
          </div>

          <h1 class="ui-dtitle" :dir="dir" :style="dirStyle">{{ display.title || product.esin }}</h1>

          <!-- 原文对照：默认折叠，避免长期占用首屏 -->
          <div v-if="sourceTitle || sourceDesc">
            <button type="button" class="ui-btn ui-btn-sm ui-btn-ghost" @click="showOriginal = !showOriginal">
              {{ showOriginal ? T('hideOriginal') : T('showOriginal') }}
            </button>
            <div v-if="showOriginal" class="ui-orig" style="margin-top: 8px">
              <b>{{ T('original') }}：</b>{{ sourceTitle }}<template v-if="sourceDesc"> · {{ sourceDesc }}</template>
            </div>
          </div>

          <p class="ui-ddesc" :dir="dir" :style="dirStyle">{{ display.desc || t.productDetail.noDescription }}</p>

          <div class="price-row">
            <div class="ui-dprice">
              {{ priceText }} <small>{{ priceMetaText }}</small>
            </div>
          </div>

          <div class="ui-mgrid">
            <div class="m">
              {{ t.productDetail.rating }}
              <b class="muted">{{ T('noRating') }}</b>
            </div>
            <div class="m">
              <span :title="T('esinFull')" style="cursor: help">{{ T('esin') }}</span>
              <b>{{ product.esin || '—' }}</b>
            </div>
            <div class="m">
              {{ T('market') }}
              <b>{{ marketText }}</b>
            </div>
            <div class="m">
              {{ T('langsAvailable') }}
              <b>{{ availableLangs.length }} / 13</b>
            </div>
          </div>

          <!-- 交易区：没有交易表，按钮保留位置并禁用，不做假成功 -->
          <div class="ui-buyrow">
            <div class="ui-qty">
              <button type="button" disabled aria-label="减少">−</button>
              <span>1</span>
              <button type="button" disabled aria-label="增加">+</button>
            </div>
            <button type="button" class="ui-btn ui-btn-dis grow" disabled>{{ T('addToCart') }}</button>
            <button type="button" class="ui-btn ui-btn-dis grow" disabled>{{ T('buyNow') }}</button>
          </div>
          <div class="ui-note" style="margin-top: 12px">
            {{ T('tradeNote') }}
          </div>
        </div>
      </div>

      <!-- 同类商品 -->
      <section class="related">
        <div class="related-head">
          <h2>{{ T('related') }}</h2>
          <span class="ui-sub">{{ T('relatedHint') }}</span>
        </div>
        <div v-if="related.length" class="ui-rgrid">
          <ProductCard
            v-for="item in related"
            :key="item.esin"
            :product="item"
            :viewer-lang="viewerLang"
            @open="changeRoute(`productDetail?esin=${encodeURIComponent($event)}`)"
          />
        </div>
        <div v-else class="ui-note">{{ T('relatedEmpty') }}</div>
      </section>
    </template>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue'
import LangSwitch from './ui/LangSwitch.vue'
import ProductCard from './ui/ProductCard.vue'
import {
  resolveDisplay,
  hasLang,
  mainImage,
  thumbImages,
  formatPrice,
  priceMeta,
  countryLabel,
  categoryLabel,
  normalizeCategory,
  categoryRequestValue,
  textDir,
  VIEW_LANGS
} from '../utils/product.js'
import { makeT } from '../i18n/ui.js'

/**
 * 买家商品详情页（原型 p8）。
 *
 * 相对原实现的改正：
 * 1. 删掉硬编码的 ★★★★☆ (4.0) 与「购买成功」假弹窗（库中无评价表、无交易表）
 * 2. 缩略图按 thumbnail_urls 实际长度渲染，不再 v-for="i in 4" 自己造 3 张
 * 3. 主图识别 picsum 占位，改为本地占位块 + 「示意图」角标（断网不会整块空白）
 * 4. 接上 13 语种标题/描述，切换语言不发请求并标出该商品确实没有的语种
 * 5. 接上 /api/recommend 做「同类商品」（该品类热销，非个性化）
 */
export default {
  name: 'ProductDetail',
  components: { LangSwitch, ProductCard },
  props: {
    t: { type: Object, default: () => ({}) },
    changeRoute: { type: Function, default: () => {} },
    productId: { type: String, default: null },
    lang: { type: String, default: 'zh' }
  },
  emits: ['update:lang'],
  setup(props, { emit }) {
    const product = ref({})
    const loading = ref(true)
    const activeImage = ref(0)
    const showOriginal = ref(false)
    const related = ref([])

    const viewerLang = computed(() => props.lang || 'zh')
    const T = (key, vars) => makeT(viewerLang.value)(key, vars)

    const display = computed(() => resolveDisplay(product.value, viewerLang.value))
    const availableLangs = computed(() => display.value.availableLangs || [])
    const dir = computed(() => textDir(display.value.lang))
    const dirStyle = computed(() => (dir.value === 'rtl' ? { textAlign: 'right' } : {}))

    const gallery = computed(() => {
      const thumbs = thumbImages(product.value)
      const main = mainImage(product.value)
      return main ? [main, ...thumbs.filter((u) => u !== main)] : thumbs
    })

    const disabledLangs = computed(() =>
      VIEW_LANGS.map((l) => l.code).filter((code) => !hasLang(product.value, code))
    )

    const priceText = computed(() => formatPrice(product.value))
    const priceMetaText = computed(() => priceMeta(product.value))
    const marketText = computed(() => {
      const country = countryLabel(product.value.country)
      const cat = categoryLabel(product.value.category, (props.t && props.t.categories) || {})
      return [country, cat].filter(Boolean).join(' · ') || '—'
    })

    const sourceTitle = computed(() => {
      const zh = product.value.zh_title
      return zh && zh !== product.value.esin ? zh : ''
    })
    const sourceDesc = computed(() => {
      const zh = product.value.zh_desc
      return zh && zh !== product.value.esin ? zh : ''
    })

    const hasSearchState = computed(() => localStorage.getItem('lastSearchState') !== null)

    const onLangChange = (code) => {
      emit('update:lang', code)
      showOriginal.value = false
    }

    const onThumbError = (index) => {
      // 单张缩略图挂了就移除，不留下破图标
      const list = [...gallery.value]
      list.splice(index, 1)
      if (activeImage.value >= list.length) activeImage.value = 0
      // 通过替换缩略图来源间接生效
      if (product.value.thumbnail_urls?.length) {
        product.value.thumbnail_urls = product.value.thumbnail_urls.filter((u) => u !== gallery.value[index])
      } else if (product.value.image_urls?.length) {
        product.value.image_urls = product.value.image_urls.filter((u) => u !== gallery.value[index])
      }
    }

    const getUserCountry = () => {
      try {
        return JSON.parse(localStorage.getItem('userProfile') || '{}').country || null
      } catch (e) {
        return null
      }
    }

    const goBackToSearch = () => {
      const raw = localStorage.getItem('lastSearchState')
      if (raw) {
        try {
          const state = JSON.parse(raw)
          const params = new URLSearchParams()
          if (state.q) params.append('q', state.q)
          if (state.category && state.category !== 'all') params.append('category', state.category)
          props.changeRoute(`searchResults?${params.toString()}`)
          return
        } catch (e) {
          /* 解析失败则回列表 */
        }
      }
      props.changeRoute('productListing')
    }

    /** 同类商品：先走推荐服务（品类热度），不足再按品类补齐 */
    const loadRelated = async (p) => {
      const cat = normalizeCategory(p?.category)
      const country = p?.country || getUserCountry() || '沙特'
      try {
        const { recommendApi, productApi } = await import('../api/index.js')
        let ids = []
        let recs = []

        if (cat === 'clothes' || cat === '3c') {
          const res = await recommendApi.getRecommend({ country, category: cat, keyword: '' })
          recs = res?.data?.data?.recommendations || []
          ids = recs.map((r) => r.id).filter(Boolean)
        }

        if (ids.length === 0) {
          const list = await productApi.getList({
            category: categoryRequestValue(p?.category),
            per_page: 5,
            language: viewerLang.value
          })
          const items = (list?.data?.products || []).filter((x) => x.esin !== p?.esin)
          if (items.length) {
            related.value = items.slice(0, 4)
            return
          }
        }

        const wanted = ids.filter((id) => id !== p?.esin).slice(0, 4)
        const detail = await Promise.allSettled(wanted.map((id) => productApi.getByEsin(id, country)))
        const hydrated = detail
          .filter((r) => r.status === 'fulfilled' && r.value?.data?.data)
          .map((r) => r.value.data.data)

        if (hydrated.length) {
          related.value = hydrated
          return
        }

        // 补齐失败时退回推荐项本身：只有 name_en / country，没有价格就不显示价格，
        // 而不是给一个前端编的价格
        related.value = (recs || [])
          .filter((r) => r.id !== p?.esin)
          .slice(0, 4)
          .map((r) => ({
            esin: r.id,
            en_title: r.name_en,
            foreign_title: r.name_en,
            category: r.category,
            country: r.country,
            image_url: r.image_url
          }))
      } catch (error) {
        console.error('[ProductDetail] 加载同类商品失败', error)
        related.value = []
      }
    }

    const fetchProductDetail = async () => {
      loading.value = true
      try {
        const { productApi } = await import('../api/index.js')
        const esin = props.productId
        if (!esin) {
          loading.value = false
          return
        }
        // 详情接口的 country 只用于币种换算，不参与过滤，可以放心传
        const response = await productApi.getByEsin(esin, getUserCountry())
        if (response?.data?.code === 200 && response.data.data) {
          product.value = response.data.data
        } else {
          product.value = { esin }
        }
        activeImage.value = 0
      } catch (error) {
        console.error('[ProductDetail] 加载详情失败', error)
        product.value = { esin: props.productId || '' }
      } finally {
        loading.value = false
      }
    }

    onMounted(async () => {
      await fetchProductDetail()
      if (product.value?.esin) loadRelated(product.value)
    })

    watch(
      () => props.productId,
      async () => {
        await fetchProductDetail()
        if (product.value?.esin) loadRelated(product.value)
      }
    )

    return {
      product,
      loading,
      activeImage,
      showOriginal,
      related,
      viewerLang,
      T,
      display,
      availableLangs,
      dir,
      dirStyle,
      gallery,
      disabledLangs,
      priceText,
      priceMetaText,
      marketText,
      sourceTitle,
      sourceDesc,
      hasSearchState,
      onLangChange,
      onThumbError,
      goBackToSearch,
      changeRoute: (...args) => props.changeRoute(...args)
    }
  }
}
</script>

<style scoped>
.lang-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-top: 6px;
}

.mgrid .m.muted,
.ui-mgrid .m b.muted {
  color: var(--ink-3);
  font-weight: 400;
}

.related {
  border-top: 1px solid var(--line-2);
  margin-top: 26px;
  padding-top: 18px;
}

.related-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.related-head h2 {
  font-size: 15px;
  font-weight: 600;
}
</style>
