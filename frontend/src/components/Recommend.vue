<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.recommend.title }}</div>
        <div class="sub">按目标国家与类目挑选候选商品，点击卡片查看本地化详情</div>
      </div>
    </div>

    <!-- 筛选条 -->
    <section class="ui-card">
      <header class="ui-card-h">
        <h3>筛选条件</h3>
        <span class="hint">国家与类目会影响推荐权重</span>
      </header>
      <div class="ui-card-b">
        <div class="rec-filter">
          <div class="ui-field">
            <label>{{ t.recommend.country }}</label>
            <select v-model="selectedCountry">
              <option v-for="country in countries" :key="country.value" :value="country.value">
                {{ country.label }}
              </option>
            </select>
          </div>
          <div class="ui-field">
            <label>{{ t.recommend.category }}</label>
            <select v-model="selectedCategory">
              <option v-for="category in categories" :key="category.value" :value="category.value">
                {{ category.label }}
              </option>
            </select>
          </div>
          <div class="ui-field rec-search">
            <label>{{ t.recommend.searchPlaceholder }}</label>
            <div class="rec-search-row">
              <input
                v-model="searchKeyword"
                :placeholder="t.recommend.searchPlaceholder"
                @keyup.enter="searchProducts"
              />
              <button @click="searchProducts" class="btn btn-primary">{{ t.recommend.searchButton }}</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 结果 -->
    <section class="ui-card">
      <header class="ui-card-h">
        <h3>{{ t.recommend.recommended }}</h3>
        <span class="hint" v-if="recommendedProducts.length">
          {{ t.recommend.resultsCount }} {{ recommendedProducts.length }} {{ t.recommend.items }}
        </span>
      </header>

      <div v-if="loading" class="rec-grid ui-card-b">
        <div v-for="n in 4" :key="'sk'+n" class="rec-card">
          <div class="skel" style="aspect-ratio: 1"></div>
          <div class="rec-body">
            <div class="skel skel-line"></div>
            <div class="skel skel-line w-40"></div>
          </div>
        </div>
      </div>

      <div v-else-if="recommendedProducts.length" class="rec-grid ui-card-b">
        <article
          v-for="(product, index) in recommendedProducts"
          :key="index"
          class="rec-card"
          @click="viewProductDetail(product)"
        >
          <div class="rec-im">
            <img :src="getProductImage(product)" :alt="product.name" loading="lazy" />
          </div>
          <div class="rec-body">
            <h3 class="rec-title">{{ product.name }}</h3>
            <div class="rec-meta">
              <span class="rec-price">{{ product.price }}</span>
              <span class="ui-tag ui-tag-mute">{{ product.country }}</span>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="ui-empty">
        <div class="ico">◎</div>
        <b>{{ t.recommend.noResults }}</b>
        <p>调整目标国家或类目后再次搜索，推荐结果会随条件变化</p>
      </div>
    </section>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'

export default {
  name: 'Recommend',
  props: {
    t: Object,
    changeRoute: Function
  },
  setup(props) {
    const recommendedProducts = ref([])
    const searchKeyword = ref('')
    const loading = ref(false)
    const selectedCountry = ref('沙特')
    const selectedCategory = ref('clothes')
    
    const countries = [
      { label: '沙特', value: '沙特' },
      { label: '阿联酋', value: '阿联酋' },
      { label: '美国', value: '美国' },
      { label: '英国', value: '英国' },
      { label: '法国', value: '法国' },
      { label: '德国', value: '德国' },
      { label: '日本', value: '日本' },
      { label: '韩国', value: '韩国' },
      { label: '俄罗斯', value: '俄罗斯' },
      { label: '泰国', value: '泰国' },
      { label: '越南', value: '越南' },
      { label: '马来西亚', value: '马来西亚' },
      { label: '印尼', value: '印尼' },
      { label: '菲律宾', value: '菲律宾' },
      { label: '新加坡', value: '新加坡' },
      { label: '中国', value: '中国' }
    ]
    
    const categories = [
      { label: '服装', value: 'clothes' },
      { label: '电子产品', value: '3c' }
    ]
    
    const fetchRecommendedProducts = async () => {
      loading.value = true
      try {
        const { recommendApi } = await import('../api/index.js')
        const response = await recommendApi.getRecommend({
          country: selectedCountry.value,
          category: selectedCategory.value,
          keyword: searchKeyword.value
        })
        
        if (response.data.code === 200) {
          // 检查response.data.data的结构
          if (response.data.data && response.data.data.recommendations) {
            recommendedProducts.value = response.data.data.recommendations.map(item => {
              // 为商品添加中文名称和价格
              let chineseName = item.name_en || item.name || `Product ${item.id}`
              let price = item.price || '¥0'
              
              // 映射英文名称到中文名称
              const nameMap = {
                'Smart Bracelet': '智能手环',
                'Wireless Earphones': '无线耳机',
                'Portable Charger': '便携充电宝',
                'Bluetooth Speaker': '蓝牙音箱',
                'Smart Light Bulb': '智能灯泡',
                'Sports Water Bottle': '运动水杯',
                'Casual T-Shirt': '休闲T恤',
                'Jeans': '牛仔裤'
              }
              
              // 映射商品到价格
              const priceMap = {
                'Smart Bracelet': '¥199',
                'Wireless Earphones': '¥399',
                'Portable Charger': '¥129',
                'Bluetooth Speaker': '¥299',
                'Smart Light Bulb': '¥89',
                'Sports Water Bottle': '¥59',
                'Casual T-Shirt': '¥99',
                'Jeans': '¥199'
              }
              
              if (nameMap[item.name_en]) {
                chineseName = nameMap[item.name_en]
              }
              
              if (priceMap[item.name_en]) {
                price = priceMap[item.name_en]
              }
              
              return {
                name: chineseName,
                description: item.description || 'No description available',
                price: price,
                id: item.id || item.product_id,
                category: item.category,
                country: item.country,
                tags: item.tags
              }
            })
          } else if (response.data.data) {
            // 兼容旧的响应格式
            recommendedProducts.value = response.data.data.map(item => {
              // 为商品添加中文名称和价格
              let chineseName = item.name_en || item.name || item.title || `Product ${item.id}`
              let price = item.price || '¥0'
              
              // 映射英文名称到中文名称
              const nameMap = {
                'Smart Bracelet': '智能手环',
                'Wireless Earphones': '无线耳机',
                'Portable Charger': '便携充电宝',
                'Bluetooth Speaker': '蓝牙音箱',
                'Smart Light Bulb': '智能灯泡',
                'Sports Water Bottle': '运动水杯',
                'Casual T-Shirt': '休闲T恤',
                'Jeans': '牛仔裤'
              }
              
              // 映射商品到价格
              const priceMap = {
                'Smart Bracelet': '¥199',
                'Wireless Earphones': '¥399',
                'Portable Charger': '¥129',
                'Bluetooth Speaker': '¥299',
                'Smart Light Bulb': '¥89',
                'Sports Water Bottle': '¥59',
                'Casual T-Shirt': '¥99',
                'Jeans': '¥199'
              }
              
              if (nameMap[item.name_en]) {
                chineseName = nameMap[item.name_en]
              }
              
              if (priceMap[item.name_en]) {
                price = priceMap[item.name_en]
              }
              
              return {
                name: chineseName,
                description: item.description || 'No description available',
                price: price,
                id: item.id || item.product_id,
                category: item.category,
                country: item.country,
                tags: item.tags
              }
            })
          }
        }
      } catch (error) {
        console.error('Error fetching recommendations:', error)
        // 使用默认数据
        recommendedProducts.value = [
          { name: '智能手环', price: '¥199', country: '中国', id: 'B08XJ8S7GQ', category: '3c' },
          { name: '无线耳机', price: '¥399', country: '中国', id: 'B07K1R3Q2T', category: '3c' },
          { name: '便携充电宝', price: '¥129', country: '中国', id: 'B09N2X4Y5Z', category: '3c' },
          { name: '蓝牙音箱', price: '¥299', country: '中国', id: 'B08M7N6O5P', category: '3c' },
          { name: '智能灯泡', price: '¥89', country: '中国', id: 'B07C8D9E0F', category: '3c' }
        ]
      } finally {
        loading.value = false
      }
    }
    
    const searchProducts = () => {
      fetchRecommendedProducts()
    }
    
    const getProductImage = (product) => {
      // 使用商品ID生成图片URL，如果有图片URL则使用，否则使用默认图片
      if (product.image_url) {
        return product.image_url
      }
      // 为不同品类生成不同的图片
      const categoryImages = {
        'clothes': `https://picsum.photos/seed/clothes${product.id || Math.random()}/400/400`,
        '3c': `https://picsum.photos/seed/3c${product.id || Math.random()}/400/400`
      }
      const imageUrl = categoryImages[product.category] || `https://picsum.photos/seed/${product.id || Math.random()}/400/400`
      console.log('Generated image URL:', imageUrl, 'for product:', product)
      return imageUrl
    }
    
    const viewProductDetail = (product) => {
      // 跳转到商品详情页面，并传递商品ID作为参数
      if (props.changeRoute) {
        props.changeRoute(`productDetail?esin=${product.id || product.product_id}`)
      }
    }
    
    onMounted(() => {
      // 不自动加载推荐数据，等待用户点击搜索按钮
    })
    
    return {
      recommendedProducts,
      searchKeyword,
      loading,
      selectedCountry,
      selectedCategory,
      countries,
      categories,
      searchProducts,
      viewProductDetail,
      getProductImage
    }
  }
}
</script>

<style scoped>
/* 筛选区：国家 / 类目 / 关键词三列，关键词占更宽 */
.rec-filter {
  display: grid;
  grid-template-columns: 200px 200px 1fr;
  gap: 14px;
  align-items: end;
}

.rec-filter .ui-field {
  margin-bottom: 0;
}

.rec-search-row {
  display: flex;
  gap: 8px;
}

.rec-search-row input {
  flex: 1;
  min-width: 0;
}

.rec-search-row .btn {
  flex: none;
  height: 36px;
}

/* 推荐结果：四列卡片，窄屏递减 */
.rec-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.rec-card {
  border: 1px solid var(--line);
  border-radius: var(--r);
  overflow: hidden;
  background: #fff;
  cursor: pointer;
  transition: border-color var(--t-base) var(--ease-out),
    box-shadow var(--t-base) var(--ease-out),
    transform var(--t-base) var(--ease-out);
}

.rec-card:hover {
  border-color: var(--line-3);
  box-shadow: var(--shadow-2);
  transform: translateY(-2px);
}

.rec-im {
  aspect-ratio: 1;
  background: linear-gradient(135deg, #eef1f6, #e2e6ee);
  overflow: hidden;
}

.rec-im img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform .5s var(--ease-out);
}

.rec-card:hover .rec-im img {
  transform: scale(1.04);
}

.rec-body {
  padding: 12px 13px 13px;
}

.rec-title {
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

.rec-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.rec-price {
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: -.2px;
}

@media (max-width: 1200px) {
  .rec-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 1024px) {
  .rec-filter { grid-template-columns: 1fr 1fr; }
  .rec-search { grid-column: 1 / -1; }
  .rec-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 560px) {
  .rec-filter { grid-template-columns: 1fr; }
  .rec-grid { grid-template-columns: 1fr; }
  .rec-title { min-height: 0; }
}
</style>
