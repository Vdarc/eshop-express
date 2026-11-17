<template>
  <div class="ui-steps" role="list" aria-label="发布流程">
    <template v-for="(step, index) in steps" :key="step.key || index">
      <div
        class="ui-stp"
        :class="{ done: index < current, on: index === current }"
        role="listitem"
        :aria-current="index === current ? 'step' : undefined"
      >
        <span class="n">{{ index < current ? '✓' : index + 1 }}</span>
        <span>{{ step.label }}</span>
      </div>
      <div v-if="index < steps.length - 1" class="ui-stp-sep" aria-hidden="true"></div>
    </template>
  </div>
</template>

<script>
/** 流程步骤条：卖家侧「基本信息 → 译文预览 → 合规预检 → 完成」。 */
export default {
  name: 'StepBar',
  props: {
    steps: { type: Array, required: true }, // [{ key, label }]
    current: { type: Number, default: 0 } // 从 0 开始
  }
}
</script>
