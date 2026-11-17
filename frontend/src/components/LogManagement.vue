<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.system.logManagement || '日志管理' }}</div>
        <div class="sub">记录平台关键操作，用于问题追溯与安全审计</div>
      </div>
      <div class="acts">
        <button class="btn btn-secondary" @click="fetchLogs">↻ 刷新</button>
      </div>
    </div>

    <section class="ui-card">
      <header class="ui-card-h">
        <h3>操作日志</h3>
        <span class="hint">第 {{ currentPage }} / {{ totalPages }} 页 · 共 {{ totalLogs }} 条</span>
      </header>
      <div class="ui-card-b tight">
        <div class="overflow-x-auto">
          <table>
            <thead>
              <tr>
                <th style="width: 76px">ID</th>
                <th>{{ t.system.operation || '操作' }}</th>
                <th style="width: 160px">{{ t.system.user || '用户' }}</th>
                <th style="width: 140px">{{ t.system.ip || 'IP' }}</th>
                <th style="width: 190px">{{ t.system.time || '时间' }}</th>
                <th style="width: 150px">{{ t.system.details || '详情' }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in logs" :key="log.id">
                <td class="mono text-gray-500">{{ log.id }}</td>
                <td><b>{{ log.action }}</b></td>
                <td>{{ log.username }}</td>
                <td class="mono text-gray-600">127.0.0.1</td>
                <td class="text-gray-600 num">{{ log.create_time }}</td>
                <td>
                  <span class="ui-tag ui-tag-info method">{{ log.method }}</span>
                </td>
              </tr>
              <tr v-if="!logs.length">
                <td colspan="6">
                  <div class="ui-empty">
                    <div class="ico">▦</div>
                    <b>{{ loading ? '正在加载日志…' : '暂无日志记录' }}</b>
                    <p>平台的接口调用与关键操作会记录在此</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="pager-bar">
        <button @click="goToPreviousPage" :disabled="currentPage === 1" class="btn btn-secondary">← 上一页</button>
        <span class="ui-sub num">{{ currentPage }} / {{ totalPages }}</span>
        <button @click="goToNextPage" :disabled="currentPage === totalPages" class="btn btn-secondary">下一页 →</button>
      </div>
    </section>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'

export default {
  name: 'LogManagement',
  props: {
    t: Object
  },
  setup() {
    const logs = ref([])
    const loading = ref(false)
    // Pagination
    const currentPage = ref(1)
    const pageSize = ref(10) // Fixed to 10 items per page
    const totalLogs = ref(0)
    const totalPages = ref(1)
    
    const fetchLogs = async () => {
      loading.value = true
      try {
        console.log('Fetching logs...', { page: currentPage.value, size: pageSize.value })
        const { logApi } = await import('../api/index.js')
        const response = await logApi.getList({
          page: currentPage.value,
          size: pageSize.value
        })
        console.log('Log fetch response:', response)
        if (response.data.code === 200) {
          logs.value = response.data.data.list || []
          totalLogs.value = response.data.data.total || 0
          totalPages.value = response.data.data.totalPages || 1
        }
      } catch (error) {
        console.error('Failed to fetch logs:', error)
        alert('获取日志列表失败: ' + (error.message || '未知错误'))
        logs.value = []
        totalLogs.value = 0
        totalPages.value = 1
      } finally {
        loading.value = false
      }
    }
    
    // Pagination handlers
    const handlePageChange = (page) => {
      currentPage.value = page
      fetchLogs()
    }
    
    const handlePageSizeChange = (size) => {
      pageSize.value = size
      currentPage.value = 1
      fetchLogs()
    }
    
    const goToFirstPage = () => {
      currentPage.value = 1
      fetchLogs()
    }
    
    const goToPreviousPage = () => {
      if (currentPage.value > 1) {
        currentPage.value--
        fetchLogs()
      }
    }
    
    const goToNextPage = () => {
      if (currentPage.value < totalPages.value) {
        currentPage.value++
        fetchLogs()
      }
    }
    
    const goToLastPage = () => {
      currentPage.value = totalPages.value
      fetchLogs()
    }
    
    const goToPage = (e) => {
      const page = parseInt(e.target.value)
      if (!isNaN(page) && page >= 1 && page <= totalPages.value) {
        currentPage.value = page
        fetchLogs()
      }
    }
    
    // 组件挂载时获取日志列表
    onMounted(() => {
      fetchLogs()
    })
    
    return {
      logs,
      loading,
      // Pagination
      currentPage,
      pageSize,
      totalLogs,
      totalPages,
      goToPreviousPage,
      goToNextPage,
      // 手动刷新（页头按钮）
      fetchLogs
    }
  }
}
</script>

<style scoped>
.pager-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 18px;
  border-top: 1px solid var(--line-2);
}

.method {
  font-family: var(--font-mono);
  font-size: 10.5px;
  letter-spacing: .02em;
}

@media (max-width: 768px) {
  .pager-bar {
    justify-content: center;
  }
}
</style>