<template>
  <div class="lazy-image-container" ref="container">
    <img 
      v-if="loaded" 
      :src="src" 
      :alt="alt" 
      class="lazy-image"
      @load="handleLoad"
    />
    <div v-else class="lazy-image-placeholder">
      <div class="loading-spinner"></div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LazyImage',
  props: {
    src: {
      type: String,
      required: true
    },
    alt: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      loaded: false,
      observer: null
    }
  },
  mounted() {
    this.initObserver()
  },
  beforeUnmount() {
    if (this.observer) {
      this.observer.disconnect()
    }
  },
  methods: {
    initObserver() {
      if ('IntersectionObserver' in window) {
        this.observer = new IntersectionObserver((entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              this.loaded = true
              this.observer.unobserve(entry.target)
            }
          })
        }, {
          threshold: 0.1
        })
        this.observer.observe(this.$refs.container)
      } else {
        // 降级方案，直接加载图片
        this.loaded = true
      }
    },
    handleLoad() {
      // 图片加载完成后的处理
    }
  }
}
</script>

<style scoped>
.lazy-image-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.lazy-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: opacity 0.3s ease;
}

.lazy-image-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #e5e7eb;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>