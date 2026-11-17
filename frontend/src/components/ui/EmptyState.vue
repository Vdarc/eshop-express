<template>
  <div class="ui-ebox">
    <div class="ico" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
           stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
        <path v-for="(d, i) in paths" :key="i" :d="d" />
      </svg>
    </div>
    <h4>{{ title }}</h4>
    <p><slot name="desc">{{ desc }}</slot></p>
    <div v-if="$slots.actions" class="ops">
      <slot name="actions"></slot>
    </div>
  </div>
</template>

<script>
/**
 * 空状态块（原型 p7 三态引导的单元）。
 * 三种成因必须分开说：品类有货 / 组合过窄 / 分类本身空。
 *
 * v2：图标由字符（⌕ / ◌ / ▢）改为线性 SVG。
 * 字符图标在不同系统字体下会退化成方块或空心方框，截图里就是「一个空正方形」，
 * 看起来像加载失败。这里按调用方传入的语义标识映射到矢量路径，调用处不用改。
 */
const ICONS = {
  search: [
    'M10.8 18a7.2 7.2 0 100-14.4 7.2 7.2 0 000 14.4z',
    'M16.2 16.2L21 21'
  ],
  filter: [
    'M4 6h16M7 12h10M10 18h4'
  ],
  box: [
    'M4 6.5h16v13H4z',
    'M4 6.5L6 3h12l2 3.5',
    'M9.5 11.5h5'
  ],
  spark: [
    'M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z'
  ]
}

const ALIAS = {
  '⌕': 'search',
  '◌': 'filter',
  '▢': 'box',
  '✦': 'spark'
}

export default {
  name: 'EmptyState',
  props: {
    // 支持语义名（search/filter/box）或历史字符（⌕/◌/▢）
    icon: { type: String, default: 'search' },
    title: { type: String, default: '' },
    desc: { type: String, default: '' }
  },
  computed: {
    paths() {
      const key = ALIAS[this.icon] || this.icon
      return ICONS[key] || ICONS.search
    }
  }
}
</script>

<style scoped>
.ico svg {
  display: block;
}
</style>
