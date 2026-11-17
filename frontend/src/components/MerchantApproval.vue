<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.merchantApproval.title }}</div>
        <div class="sub">审核商家入驻申请；通过后该账号即可发布商品与使用翻译能力</div>
      </div>
      <div class="acts">
        <span class="ui-tag ui-tag-warn">{{ total }} {{ t.merchantApproval.applications }}</span>
      </div>
    </div>

    <section class="ui-card">
      <header class="ui-card-h">
        <h3>{{ t.merchantApproval.pendingApplications }}</h3>
        <span class="hint">按提交时间倒序</span>
      </header>

      <div v-if="loading" class="ui-card-b">
        <div v-for="n in 3" :key="'sk'+n" class="skel-row">
          <div class="skel skel-line w-40"></div>
          <div class="skel skel-line"></div>
        </div>
      </div>

      <div v-else-if="applications.length === 0" class="ui-empty">
        <div class="ico">☑</div>
        <b>暂无待审核申请</b>
        <p>新的商家入驻申请会出现在这里</p>
      </div>

      <div v-else class="ui-card-b tight">
        <div class="overflow-x-auto">
          <table>
            <thead>
              <tr>
                <th>{{ t.merchantApproval.shopName }}</th>
                <th>{{ t.merchantApproval.username }}</th>
                <th>{{ t.merchantApproval.email }}</th>
                <th>{{ t.merchantApproval.mainCategory }}</th>
                <th>{{ t.merchantApproval.contactName }}</th>
                <th>{{ t.merchantApproval.contactPhone }}</th>
                <th style="width: 150px" class="text-right">{{ t.merchantApproval.actions }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="app in applications" :key="app.id">
                <td><b>{{ app.shop_name }}</b></td>
                <td>{{ app.username }}</td>
                <td class="text-gray-600">{{ app.email }}</td>
                <td>
                  <span class="ui-tag ui-tag-mute">{{ t.categories[app.main_category] || app.main_category }}</span>
                </td>
                <td>{{ app.contact_name }}</td>
                <td class="mono text-gray-600">{{ app.contact_phone }}</td>
                <td class="text-right">
                  <div class="row-ops">
                    <button @click="approveApplication(app.id)" class="ui-btn ui-btn-ok ui-btn-sm">
                      {{ t.merchantApproval.approve }}
                    </button>
                    <button @click="rejectApplication(app.id)" class="ui-btn ui-btn-danger ui-btn-sm">
                      {{ t.merchantApproval.reject }}
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div v-if="total > 0" class="pager-bar">
        <span class="ui-sub">{{ total }} {{ t.merchantApproval.applications }}</span>
        <button @click="changePage(page - 1)" :disabled="page === 1" class="btn btn-secondary">
          ← {{ t.common.previousPage }}
        </button>
        <span class="ui-sub num">{{ page }} / {{ totalPages }}</span>
        <button @click="changePage(page + 1)" :disabled="page === totalPages" class="btn btn-secondary">
          {{ t.common.nextPage }} →
        </button>
      </div>
    </section>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'

export default {
  name: 'MerchantApproval',
  props: {
    t: Object
  },
  setup() {
    const applications = ref([])
    const loading = ref(false)
    const page = ref(1)
    const size = ref(10)
    const total = ref(0)
    const totalPages = ref(0)
    
    const fetchApplications = async () => {
      loading.value = true
      try {
        const { merchantApi } = await import('../api/index.js')
        const response = await merchantApi.getApplications({ page: page.value, size: size.value })
        
        if (response.data.code === 200) {
          applications.value = response.data.data.list
          total.value = response.data.data.total
          totalPages.value = response.data.data.totalPages
        } else {
          console.error('Failed to fetch applications:', response.data.msg)
        }
      } catch (error) {
        console.error('Failed to fetch applications:', error)
      } finally {
        loading.value = false
      }
    }
    
    const approveApplication = async (applicationId) => {
      if (confirm('确定要批准这个商家申请吗？')) {
        try {
          const { merchantApi } = await import('../api/index.js')
          const response = await merchantApi.approve({ application_id: applicationId })
          
          if (response.data.code === 200) {
            alert('批准成功')
            fetchApplications()
          } else {
            alert('批准失败: ' + response.data.msg)
          }
        } catch (error) {
          console.error('Failed to approve application:', error)
          alert('批准失败: ' + error.message)
        }
      }
    }
    
    const rejectApplication = async (applicationId) => {
      if (confirm('确定要拒绝这个商家申请吗？')) {
        try {
          const { merchantApi } = await import('../api/index.js')
          const response = await merchantApi.reject({ application_id: applicationId })
          
          if (response.data.code === 200) {
            alert('拒绝成功')
            fetchApplications()
          } else {
            alert('拒绝失败: ' + response.data.msg)
          }
        } catch (error) {
          console.error('Failed to reject application:', error)
          alert('拒绝失败: ' + error.message)
        }
      }
    }
    
    const changePage = (newPage) => {
      if (newPage >= 1 && newPage <= totalPages.value) {
        page.value = newPage
        fetchApplications()
      }
    }
    
    onMounted(() => {
      fetchApplications()
    })
    
    return {
      applications,
      loading,
      page,
      size,
      total,
      totalPages,
      fetchApplications,
      approveApplication,
      rejectApplication,
      changePage
    }
  }
}
</script>
<style scoped>
.row-ops {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-end;
}

.pager-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 18px;
  border-top: 1px solid var(--line-2);
  flex-wrap: wrap;
}

.skel-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--line-2);
}

.skel-row:last-child { border-bottom: 0; }
</style>
