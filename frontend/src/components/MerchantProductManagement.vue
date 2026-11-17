<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.merchantProducts.title }}</div>
        <div class="sub">管理已发布的商品：编辑译文、下架不再销售的商品</div>
      </div>
      <div class="acts">
        <div class="search-box">
          <span aria-hidden="true">⌕</span>
          <input
            type="text"
            v-model="searchQuery"
            :placeholder="t.merchantProducts.searchPlaceholder"
            @keyup.enter="handleSearch"
          />
          <button class="go" @click="handleSearch">{{ t.merchantProducts.searchButton }}</button>
        </div>
      </div>
    </div>

    <!-- 加载态 -->
    <div class="mp-grid" v-if="loading">
      <div v-for="n in 4" :key="'sk'+n" class="mp-card">
        <div class="skel" style="aspect-ratio: 4 / 3"></div>
        <div class="mp-body">
          <div class="skel skel-line"></div>
          <div class="skel skel-line w-40"></div>
        </div>
      </div>
    </div>

    <!-- 商品列表 -->
    <div class="mp-grid" v-else-if="products.length">
      <article
        v-for="product in products"
        :key="product.esin"
        class="mp-card"
      >
        <div class="mp-im">
          <img :src="getProductImage(product)" :alt="product.zh_title || product.en_title" loading="lazy" />
        </div>
        <div class="mp-body">
          <h3 class="mp-title">{{ product.zh_title || product.en_title }}</h3>
          <div class="mp-meta">
            <span class="ui-tag ui-tag-mute">{{ getCategoryName(product.category) }}</span>
            <span class="ui-sub">{{ product.country }}</span>
          </div>
          <div class="mp-ops">
            <button class="ui-btn ui-btn-sec ui-btn-sm" @click="editProduct(product)">
              {{ t.merchantProducts.editButton }}
            </button>
            <button class="ui-btn ui-btn-danger ui-btn-sm" @click="unpublishProduct(product.esin)">
              {{ t.merchantProducts.unpublishButton }}
            </button>
          </div>
        </div>
      </article>
    </div>

    <!-- 无结果状态 -->
    <div class="ui-card" v-else>
      <div class="ui-empty">
        <div class="ico">▦</div>
        <b>{{ t.merchantProducts.noProducts }}</b>
        <p>通过「商品发布」创建第一个商品，发布后即可在此管理译文与上下架状态</p>
      </div>
    </div>

    <!-- 编辑商品对话框 -->
    <div class="ui-modal-mask" v-if="isEditing" @click.self="isEditing = false">
      <div class="ui-modal">
        <header class="ui-modal-h">
          <h3>{{ t.merchantProducts.editModalTitle }}</h3>
          <button class="x" type="button" aria-label="关闭" @click="isEditing = false">✕</button>
        </header>
        <form @submit.prevent="saveProduct">
          <div class="ui-modal-b">
            <div class="ui-field">
              <label>{{ t.merchantProducts.titleChinese }}</label>
              <input v-model="editForm.zh_title" type="text" required />
            </div>
            <div class="ui-field">
              <label>{{ t.merchantProducts.titleForeign.replace('{language}', getLanguageLabel(editForm.target_language)) }}</label>
              <input v-model="editForm.foreign_title" type="text" :dir="editForm.target_language === 'ar' ? 'rtl' : 'ltr'" required />
            </div>
            <div class="ui-field">
              <label>{{ t.merchantProducts.descChinese }}</label>
              <textarea v-model="editForm.zh_desc" rows="4" required></textarea>
            </div>
            <div class="ui-field">
              <label>{{ t.merchantProducts.descForeign.replace('{language}', getLanguageLabel(editForm.target_language)) }}</label>
              <textarea v-model="editForm.foreign_desc" rows="4" :dir="editForm.target_language === 'ar' ? 'rtl' : 'ltr'" required></textarea>
            </div>
            <div class="ui-field">
              <label>{{ t.merchantProducts.categoryLabel }}</label>
              <select v-model="editForm.category" required>
                <option v-for="(name, key) in t.merchantProducts.categories" :key="key" :value="key">{{ name }}</option>
              </select>
            </div>
          </div>
          <footer class="ui-modal-f">
            <button type="button" class="btn btn-secondary" @click="isEditing = false">{{ t.merchantProducts.cancelButton }}</button>
            <button type="submit" class="btn btn-primary">{{ t.merchantProducts.saveButton }}</button>
          </footer>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'

export default {
  name: 'MerchantProductManagement',
  props: {
    t: Object,
    changeRoute: Function
  },
  setup(props) {
    const products = ref([])
    const loading = ref(true)
    const searchQuery = ref('')
    const isEditing = ref(false)
    const editForm = ref({})

    // 获取商品图片
    const getProductImage = (product) => {
      return product.image_url || `https://picsum.photos/seed/${product.esin}/300/300`
    }

    // 获取分类名称
    const getCategoryName = (category) => {
      const categoryMap = {
        'clothes': '服装',
        '3c': '3C电子产品',
        'home': '家居用品',
        'beauty': '美妆',
        'baby': '母婴',
        'sports': '运动',
        'car': '汽车用品'
      }
      return categoryMap[category] || category
    }

    // 获取语言标签
    const getLanguageLabel = (lang) => {
      const langMap = {
        'en': '英文',
        'ar': '阿拉伯语',
        'fr': '法语',
        'de': '德语',
        'ja': '日语',
        'ko': '韩语',
        'ru': '俄语',
        'th': '泰语',
        'vi': '越南语',
        'ms': '马来语',
        'id': '印尼语',
        'tl': '菲律宾语'
      }
      return langMap[lang] || '外文'
    }

    // 加载商品数据
    const loadProducts = async () => {
      loading.value = true
      try {
        const { productApi } = await import('../api/index.js')
        console.log('Requesting products from /api/merchant/products')
        const response = await productApi.getMerchantProducts()
        console.log('Response data:', response.data)

        // 后端返回 {code:200, data:{products,total,current,per_page,pages}}，
        // 商品数组在 response.data.data.products；只读 response.data.products 会恒为空
        const payload = response.data?.data ?? response.data
        if (payload) {
          products.value = payload.products || []
          console.log('Loaded products:', products.value)
        }
      } catch (error) {
        console.error('Error loading products:', error)
      } finally {
        loading.value = false
      }
    }

    // 处理搜索
    const handleSearch = async () => {
      loading.value = true
      try {
        const { productApi } = await import('../api/index.js')
        const response = await productApi.getMerchantProducts({
          search: searchQuery.value
        })

        const payload = response.data?.data ?? response.data
        if (payload) {
          products.value = payload.products || []
        }
      } catch (error) {
        console.error('Error searching products:', error)
      } finally {
        loading.value = false
      }
    }

    // 编辑商品
    const editProduct = (product) => {
      editForm.value = { ...product }
      isEditing.value = true
    }

    // 保存商品
    const saveProduct = async () => {
      try {
        const { productApi } = await import('../api/index.js')
        // 使用update接口而不是publish接口
        const response = await productApi.update(editForm.value.esin, editForm.value)

        if (response.data) {
          await loadProducts()
          isEditing.value = false
        }
      } catch (error) {
        console.error('Error saving product:', error)
      }
    }

    // 下架商品
    const unpublishProduct = async (esin) => {
      if (confirm('确定要下架此商品吗？')) {
        try {
          const { productApi } = await import('../api/index.js')
          // 商家自有商品走 DELETE /api/merchant/products/{esin}（校验所有权）
          const response = await productApi.deleteMerchantProduct(esin)

          if (response.data?.code === 200) {
            await loadProducts()
          } else {
            alert('下架失败：' + (response.data?.msg || '未知错误'))
          }
        } catch (error) {
          console.error('Error unpublishing product:', error)
          alert('下架失败：' + (error.response?.data?.msg || error.message))
        }
      }
    }

    onMounted(() => {
      loadProducts()
    })

    return {
      products,
      loading,
      searchQuery,
      isEditing,
      editForm,
      getProductImage,
      getCategoryName,
      getLanguageLabel,
      handleSearch,
      editProduct,
      saveProduct,
      unpublishProduct
    }
  }
}
</script>

<style scoped>
/* 搜索框：与历史记录页同一套控件，输入框 + 内嵌主按钮 */
.search-box {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 4px 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: #fff;
  color: var(--ink-3);
  font-size: 12.5px;
  transition: border-color var(--t-fast) var(--ease-out), box-shadow var(--t-fast) var(--ease-out);
}

.search-box:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring);
}

.search-box input {
  width: 200px;
  height: 30px;
  min-height: 0;
  border: 0;
  padding: 0 2px;
  background: transparent;
  font-size: 13px;
  box-shadow: none;
}

.search-box .go {
  height: 26px;
  padding: 0 14px;
  border-radius: var(--r-pill);
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  flex: none;
  transition: background-color var(--t-fast) var(--ease-out);
}

.search-box .go:hover { background: var(--brand-hover); }

/* 商品卡网格 */
.mp-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.mp-card {
  background: #fff;
  border: 1px solid var(--line);
  border-radius: var(--r);
  overflow: hidden;
  transition: border-color var(--t-base) var(--ease-out),
    box-shadow var(--t-base) var(--ease-out),
    transform var(--t-base) var(--ease-out);
}

.mp-card:hover {
  border-color: var(--line-3);
  box-shadow: var(--shadow-2);
  transform: translateY(-2px);
}

.mp-im {
  aspect-ratio: 4 / 3;
  background: linear-gradient(135deg, #eef1f6, #e2e6ee);
  overflow: hidden;
}

.mp-im img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform .5s var(--ease-out);
}

.mp-card:hover .mp-im img {
  transform: scale(1.04);
}

.mp-body {
  padding: 12px 13px 13px;
}

.mp-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--ink);
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 39px;
  word-break: break-word;
}

.mp-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 11px;
  flex-wrap: wrap;
}

.mp-ops {
  display: flex;
  gap: 8px;
}

.mp-ops .ui-btn {
  flex: 1;
}

@media (max-width: 1400px) {
  .mp-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 1024px) {
  .mp-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 560px) {
  .mp-grid { grid-template-columns: 1fr; }
  .search-box input { width: 120px; }
  .mp-title { min-height: 0; }
}
</style>
