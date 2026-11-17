<template>
  <article class="ui-rcard" @click="$emit('open', product.esin)">
    <div class="im">
      <span class="lb" :class="{ warn: display.isFallback }">{{ badge }}</span>
      <img v-if="imageUrl && !imgFailed" :src="imageUrl" :alt="plainTitle" loading="lazy" @error="imgFailed = true" />
      <span v-else>商品主图</span>
    </div>
    <div class="in">
      <h3 class="tt" :dir="dir" :style="dirStyle">
        <span v-if="highlightHtml" v-html="highlightHtml"></span>
        <span v-else>{{ plainTitle }}</span>
        <span v-if="display.isFallback" class="fallback"> · 回退 {{ langName(display.lang) }}</span>
      </h3>
      <div class="mt">
        <span>{{ metaText }}</span>
        <span class="pr">{{ priceText }}</span>
      </div>
    </div>
  </article>
</template>

<script>
import { computed, ref } from 'vue'
import {
  resolveDisplay,
  pickHighlight,
  mainImage,
  formatPrice,
  countryLabel,
  LANG_NATIVE,
  textDir
} from '../../utils/product.js'

/**
 * 买家侧商品卡（搜索页 / 列表页 / 详情页「同类商品」三处复用）。
 *
 * 三件事由这里统一，避免各页面各写一份：
 * 1. 标题取哪个语种、是否发生了语言回退（后端由卖家发布语种决定，必须如实标注）
 * 2. 后端返回的 <em> 高亮是否渲染（原实现从未渲染，买家看不出为何命中）
 * 3. 占位图识别（picsum 占位改为本地块，断网时不至于整块空白）
 */
export default {
  name: 'ProductCard',
  props: {
    product: { type: Object, required: true },
    viewerLang: { type: String, default: 'zh' }
  },
  emits: ['open'],
  setup(props) {
    // 真实图加载失败时降级为本地占位块，不留空白
    const imgFailed = ref(false)

    const display = computed(() => resolveDisplay(props.product, props.viewerLang))
    const plainTitle = computed(() => display.value.title || '暂无标题')
    const imageUrl = computed(() => mainImage(props.product))
    const highlightHtml = computed(() => pickHighlight(props.product, props.viewerLang))
    const priceText = computed(() => formatPrice(props.product))
    const dir = computed(() => textDir(display.value.lang))
    const dirStyle = computed(() => (dir.value === 'rtl' ? { textAlign: 'right' } : {}))

    const langName = (code) => LANG_NATIVE[code] || code

    // 徽标只标「这张卡实际是什么语言」，国家放到下方 meta，避免两处重复
    const badge = computed(() => {
      if (display.value.isFallback) return `暂无${langName(props.viewerLang)}`
      return langName(display.value.lang)
    })

    const metaText = computed(() => {
      const country = countryLabel(props.product.country)
      // 回退时如实说明「实际用的是哪个语种」，正常时只留国家，避免与徽标重复
      if (display.value.isFallback) {
        return [country, `回退 ${langName(display.value.lang)}`].filter(Boolean).join(' · ')
      }
      return country || ''
    })

    return {
      imgFailed,
      display,
      plainTitle,
      imageUrl,
      highlightHtml,
      priceText,
      dir,
      dirStyle,
      badge,
      metaText,
      langName
    }
  }
}
</script>

<style scoped>
.fallback {
  color: var(--warn);
  font-size: 11px;
  font-weight: 400;
}
</style>
