<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.glossary.title }}</div>
        <div class="sub">术语在翻译前作为约束注入，保证品牌词与品类词的译法一致</div>
      </div>
      <div class="acts">
        <button class="btn btn-primary" @click="showAddModal = true">＋ {{ t.glossary.addTerm }}</button>
      </div>
    </div>

    <section class="ui-card">
      <header class="ui-card-h">
        <h3>术语列表</h3>
        <span class="hint">共 {{ glossaryTerms.length }} 条</span>
      </header>

      <!-- 搜索和筛选 -->
      <div class="g-toolbar">
        <div class="search-box">
          <span aria-hidden="true">⌕</span>
          <input
            type="text"
            v-model="searchQuery"
            :placeholder="t.glossary.search"
            @keyup.enter="loadGlossary"
          />
        </div>
        <select v-model="filterCategory" class="g-select">
          <option value="">全部分类</option>
          <option v-for="(name, key) in t.categories" :key="key" :value="key">{{ name }}</option>
        </select>
        <select v-model="filterLanguage" class="g-select">
          <option value="">全部语言</option>
          <option v-for="(name, key) in t.languages" :key="key" :value="key">{{ name }}</option>
        </select>
        <button class="btn btn-secondary" @click="loadGlossary">刷新</button>
      </div>

      <!-- 术语列表 -->
      <div class="ui-card-b tight">
        <div class="overflow-x-auto">
          <table>
            <thead>
              <tr>
                <th>{{ t.glossary.chineseTerm }}</th>
                <th>{{ t.glossary.foreignTerm }}</th>
                <th style="width: 130px">{{ t.glossary.language }}</th>
                <th style="width: 120px">{{ t.glossary.category }}</th>
                <th>{{ t.glossary.note }}</th>
                <th style="width: 110px" class="text-right">{{ t.glossary.actions }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="6">
                  <div class="ui-empty" style="padding: 30px 12px">
                    <b>正在加载术语…</b>
                  </div>
                </td>
              </tr>
              <tr v-else-if="glossaryTerms.length === 0">
                <td colspan="6">
                  <div class="ui-empty">
                    <div class="ico">◧</div>
                    <b>{{ t.common.noData }}</b>
                    <p>添加品牌词、品类词的固定译法，翻译时会优先采用</p>
                  </div>
                </td>
              </tr>
              <template v-else>
              <tr v-for="term in glossaryTerms" :key="term.id">
                <td><b>{{ term.chinese }}</b></td>
                <td :dir="term.language === 'ar' ? 'rtl' : 'ltr'">{{ term.foreign }}</td>
                <td><span class="ui-tag ui-tag-brand">{{ t.languages[term.language] || term.language }}</span></td>
                <td><span class="ui-tag ui-tag-mute">{{ t.categories[term.category] || term.category }}</span></td>
                <td class="text-gray-600">{{ term.note || '—' }}</td>
                <td class="text-right">
                  <div class="row-ops">
                    <button class="ui-btn-link" @click="editTerm(term)">编辑</button>
                    <button class="ui-btn-link danger" @click="deleteTerm(term.id)">删除</button>
                  </div>
                </td>
              </tr>
              </template>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- 添加/编辑模态框 -->
    <div class="ui-modal-mask" v-if="showAddModal || showEditModal" @click.self="closeModal">
      <div class="ui-modal">
        <header class="ui-modal-h">
          <h3>{{ showAddModal ? t.glossary.addTerm : t.glossary.editTerm }}</h3>
          <button class="x" type="button" aria-label="关闭" @click="closeModal">✕</button>
        </header>
        <form @submit.prevent="saveTerm">
          <div class="ui-modal-b">
            <div class="ui-g2">
              <div class="ui-field">
                <label>{{ t.glossary.chineseTerm }}<span class="req">*</span></label>
                <input type="text" v-model="formData.chinese" required />
              </div>
              <div class="ui-field">
                <label>{{ t.glossary.foreignTerm }}<span class="req">*</span></label>
                <input type="text" v-model="formData.foreign" :dir="formData.language === 'ar' ? 'rtl' : 'ltr'" required />
              </div>
            </div>
            <div class="ui-g2">
              <div class="ui-field">
                <label>{{ t.glossary.language }}</label>
                <select v-model="formData.language" required>
                  <option v-for="(name, key) in t.languages" :key="key" :value="key">{{ name }}</option>
                </select>
              </div>
              <div class="ui-field">
                <label>{{ t.glossary.category }}</label>
                <select v-model="formData.category" required>
                  <option v-for="(name, key) in t.categories" :key="key" :value="key">{{ name }}</option>
                </select>
              </div>
            </div>
            <div class="ui-field">
              <label>{{ t.glossary.note }}</label>
              <textarea v-model="formData.note" rows="3" placeholder="选填：使用场景、禁用译法等说明"></textarea>
            </div>
          </div>
          <footer class="ui-modal-f">
            <button type="button" class="btn btn-secondary" @click="closeModal">{{ t.common.cancel }}</button>
            <button type="submit" class="btn btn-primary">{{ t.common.save }}</button>
          </footer>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'

export default {
  name: 'Glossary',
  props: {
    t: Object
  },
  setup(props) {
    const glossaryTerms = ref([])
    const loading = ref(false)
    const searchQuery = ref('')
    const filterCategory = ref('')
    const filterLanguage = ref('')
    const showAddModal = ref(false)
    const showEditModal = ref(false)
    const formData = ref({
      id: null,
      chinese: '',
      foreign: '',
      language: 'en',
      category: 'clothes',
      note: ''
    })
    
    // 加载术语库
    const loadGlossary = async () => {
      loading.value = true
      try {
        const { glossaryApi } = await import('../api/index.js')
        const response = await glossaryApi.getList({
          search: searchQuery.value,
          category: filterCategory.value,
          language: filterLanguage.value
        })
        
        if (response.data.code === 200) {
          glossaryTerms.value = response.data.data
        }
      } catch (error) {
        console.error('加载术语库失败:', error)
      } finally {
        loading.value = false
      }
    }
    
    // 添加术语
    const saveTerm = async () => {
      try {
        const { glossaryApi } = await import('../api/index.js')
        let response
        
        if (showAddModal.value) {
          // 添加新术语
          response = await glossaryApi.add(formData.value)
        } else {
          // 更新术语
          response = await glossaryApi.update(formData.value)
        }
        
        if (response.data.code === 200) {
          loadGlossary()
          closeModal()
        } else {
          alert(response.data.msg || response.data.error || '操作失败')
        }
      } catch (error) {
        console.error('保存术语失败:', error)
        alert('保存失败: ' + (error.message || '未知错误'))
      }
    }
    
    // 编辑术语
    const editTerm = (term) => {
      formData.value = {
        id: term.id,
        chinese: term.chinese,
        foreign: term.foreign,
        language: term.language,
        category: term.category,
        note: term.note
      }
      showEditModal.value = true
    }
    
    // 删除术语
    const deleteTerm = async (id) => {
      if (confirm('确定要删除这个术语吗？')) {
        try {
          const { glossaryApi } = await import('../api/index.js')
          const response = await glossaryApi.delete({ id })
          
          if (response.data.code === 200) {
            loadGlossary()
          } else {
            alert(response.data.msg || response.data.error || '删除失败')
          }
        } catch (error) {
          console.error('删除术语失败:', error)
          alert('删除失败: ' + (error.message || '未知错误'))
        }
      }
    }
    
    // 关闭模态框
    const closeModal = () => {
      showAddModal.value = false
      showEditModal.value = false
      formData.value = {
        id: null,
        chinese: '',
        foreign: '',
        language: 'en',
        category: 'clothes',
        note: ''
      }
    }
    
    onMounted(() => {
      loadGlossary()
    })
    
    return {
      glossaryTerms,
      loading,
      searchQuery,
      filterCategory,
      filterLanguage,
      showAddModal,
      showEditModal,
      formData,
      loadGlossary,
      saveTerm,
      editTerm,
      deleteTerm,
      closeModal
    }
  }
}
</script>

<style scoped>
/* 筛选工具条：与表头分隔，避免搜索框和下拉直接贴在一起 */
.g-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--line-2);
  flex-wrap: wrap;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--line-3);
  border-radius: var(--r-pill);
  background: #fff;
  color: var(--ink-3);
  font-size: 12.5px;
  flex: 1;
  min-width: 200px;
  max-width: 340px;
  transition: border-color var(--t-fast) var(--ease-out), box-shadow var(--t-fast) var(--ease-out);
}

.search-box:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring);
}

.search-box input {
  flex: 1;
  min-width: 0;
  height: 30px;
  min-height: 0;
  border: 0;
  padding: 0;
  background: transparent;
  font-size: 13px;
  box-shadow: none;
}

.g-select {
  width: 150px;
  height: 34px;
  font-size: 12.5px;
  flex: none;
}

.row-ops {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .g-toolbar { padding: 12px; }
  .search-box { max-width: none; }
  .g-select { width: calc(50% - 5px); }
}
</style>
