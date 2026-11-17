<template>
  <div class="ui-lseg" role="group" aria-label="界面语言">
    <button
      v-for="item in resolved"
      :key="item.code"
      type="button"
      :class="{
        on: item.code === modelValue,
        off: isDisabled(item.code)
      }"
      :disabled="isDisabled(item.code)"
      :title="isDisabled(item.code) ? copy.noTranslation : item.label"
      @click="select(item.code)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<script>
import { computed } from 'vue'
import { VIEW_LANGS } from '../../utils/product.js'

/**
 * 语言档位切换。
 *
 * 注意口径：界面只有 3 档（中/英/阿），而商品字段存了 13 个语种。
 * 所以这里切换的是「展示语言」，不是「商品支持的语言数」。
 * disabled 用于详情页标出该商品确实没有的语种译文（原型 p8 的 Français 删除线）。
 */
export default {
  name: 'LangSwitch',
  props: {
    modelValue: { type: String, default: 'zh' },
    langs: { type: Array, default: () => VIEW_LANGS },
    disabled: { type: Array, default: () => [] }
  },
  emits: ['update:modelValue', 'change'],
  setup(props, { emit }) {
    const copy = { noTranslation: '该商品没有这个语种的译文' }

    const resolved = computed(() =>
      (props.langs || []).map((l) => (typeof l === 'string' ? { code: l, label: l } : l))
    )

    const isDisabled = (code) => (props.disabled || []).includes(code)

    const select = (code) => {
      if (isDisabled(code) || code === props.modelValue) return
      emit('update:modelValue', code)
      emit('change', code)
    }

    return { resolved, copy, isDisabled, select }
  }
}
</script>
