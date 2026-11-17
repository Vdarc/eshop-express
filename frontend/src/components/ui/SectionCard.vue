<template>
  <section class="ui-card">
    <header v-if="title || $slots.head" class="ui-card-h">
      <h3 v-if="title">{{ title }}</h3>
      <slot name="head"></slot>
      <span v-if="hint" class="hint">{{ hint }}</span>
    </header>
    <div class="ui-card-b" :class="{ tight }">
      <slot></slot>
    </div>
  </section>
</template>

<script>
/** 带标题栏的卡片壳。卖家侧 8 个卡片、买家侧详情页都在用同一套表头。 */
export default {
  name: 'SectionCard',
  props: {
    title: { type: String, default: '' },
    hint: { type: String, default: '' },
    // true 时去掉内边距，用于卡片内直接嵌列表/表格
    tight: { type: Boolean, default: false }
  }
}
</script>
