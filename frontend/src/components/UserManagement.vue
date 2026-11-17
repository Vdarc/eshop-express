<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.userManagement.title }}</div>
        <div class="sub">管理平台账号、角色与启用状态；新增账号默认需要强密码</div>
      </div>
      <div class="acts">
        <button class="btn btn-primary" @click="addUser">＋ {{ t.userManagement.addUser }}</button>
      </div>
    </div>

    <section class="ui-card">
      <header class="ui-card-h">
        <h3>账号列表</h3>
        <span class="hint">共 {{ totalUsers }} 个账号 · 第 {{ currentPage }} / {{ totalPages }} 页</span>
      </header>
      <div class="ui-card-b tight">
        <div class="overflow-x-auto">
          <table>
            <thead>
              <tr>
                <th>{{ t.userManagement.table.username }}</th>
                <th style="width: 200px">{{ t.userManagement.table.role }}</th>
                <th style="width: 110px">{{ t.userManagement.table.status }}</th>
                <th style="width: 180px">{{ t.userManagement.table.createTime }}</th>
                <th style="width: 150px" class="text-right">{{ t.userManagement.table.actions }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="user in users" :key="user.user_id">
                <td>
                  <div class="cell-user">
                    <span class="ava">{{ (user.username || '·').charAt(0).toUpperCase() }}</span>
                    <span>{{ user.username }}</span>
                  </div>
                </td>
                <td>
                  <span v-for="r in user.roles" :key="r" class="ui-tag ui-tag-mute role-tag">{{ r }}</span>
                  <span v-if="!user.roles || !user.roles.length" class="text-gray-400">—</span>
                </td>
                <td>
                  <span class="ui-dot" :class="user.status ? 'ok' : 'off'">
                    {{ user.status ? t.common.statusActive : t.common.statusInactive }}
                  </span>
                </td>
                <td class="text-gray-600 num">{{ user.create_time }}</td>
                <td class="text-right">
                  <div class="row-ops">
                    <button class="ui-btn-link" @click="editUser(user)">{{ t.userManagement.actions.edit }}</button>
                    <button class="ui-btn-link danger" @click="deleteUser(user.user_id)">{{ t.userManagement.actions.delete }}</button>
                  </div>
                </td>
              </tr>
              <tr v-if="!users.length">
                <td colspan="5">
                  <div class="ui-empty">
                    <div class="ico">⚇</div>
                    <b>{{ loading ? '正在加载账号…' : '暂无账号数据' }}</b>
                    <p>点击右上角「新增用户」创建第一个平台账号</p>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="ui-card-b pager-bar" v-if="totalPages > 1">
        <button @click="goToPreviousPage" :disabled="currentPage === 1" class="btn btn-secondary">
          ← 上一页
        </button>
        <span class="ui-sub num">{{ currentPage }} / {{ totalPages }}</span>
        <button @click="goToNextPage" :disabled="currentPage === totalPages" class="btn btn-secondary">
          下一页 →
        </button>
      </div>
    </section>

    <!-- 新增用户 -->
    <div v-if="showAddForm" class="ui-modal-mask" @click.self="showAddForm = false">
      <div class="ui-modal">
        <header class="ui-modal-h">
          <h3>{{ t.userManagement.modal.addTitle }}</h3>
          <button class="x" type="button" aria-label="关闭" @click="showAddForm = false">✕</button>
        </header>
        <div class="ui-modal-b">
          <div class="ui-field">
            <label>{{ t.userManagement.modal.username }}<span class="req">*</span></label>
            <input v-model="newUser.username" type="text" placeholder="用于登录的账号" />
          </div>
          <div class="ui-field">
            <label>{{ t.userManagement.modal.password }}<span class="req">*</span></label>
            <input v-model="newUser.password" type="password" placeholder="8-32 位，含大小写字母、数字与特殊字符" @input="checkPasswordStrength(newUser.password)" />
            <div v-if="passwordStrength" class="strength-line">
              <span class="ui-sub">密码强度</span>
              <span :class="['ui-tag', passwordStrength === 'strong' ? 'ui-tag-ok' : passwordStrength === 'medium' ? 'ui-tag-warn' : 'ui-tag-danger']">
                {{ passwordStrengthText }}
              </span>
            </div>
            <div v-if="passwordStrength === 'weak'" class="tip err">密码强度太弱，请包含大小写字母、数字和特殊字符（长度 8-32 位）</div>
            <div v-else-if="passwordStrength === 'medium'" class="tip warn">密码强度中等，建议补充特殊字符提升安全性</div>
            <div v-else-if="passwordStrength === 'strong'" class="tip ok">密码强度良好</div>
          </div>
          <div class="ui-field">
            <label>邮箱</label>
            <input v-model="newUser.email" type="email" placeholder="用于接收通知" required />
          </div>
          <div class="ui-g2">
            <div class="ui-field">
              <label>状态</label>
              <select v-model="newUser.status">
                <option :value="1">正常</option>
                <option :value="0">禁用</option>
              </select>
            </div>
            <div class="ui-field">
              <label>角色（可多选）</label>
              <select v-model="newUser.role_ids" multiple class="multi">
                <option v-for="role in roles" :key="role.role_id" :value="role.role_id">{{ role.role_name }}</option>
              </select>
            </div>
          </div>
        </div>
        <footer class="ui-modal-f">
          <button class="btn btn-secondary" @click="showAddForm = false">{{ t.common.cancel }}</button>
          <button class="btn btn-primary" @click="saveNewUser">{{ t.common.save }}</button>
        </footer>
      </div>
    </div>

    <!-- 编辑用户 -->
    <div v-if="showEditForm" class="ui-modal-mask" @click.self="showEditForm = false">
      <div class="ui-modal">
        <header class="ui-modal-h">
          <h3>{{ t.userManagement.modal.editTitle }}</h3>
          <button class="x" type="button" aria-label="关闭" @click="showEditForm = false">✕</button>
        </header>
        <div class="ui-modal-b">
          <div class="ui-field">
            <label>{{ t.userManagement.modal.username }}</label>
            <input v-model="currentUser.username" type="text" />
          </div>
          <div class="ui-field">
            <label>{{ t.userManagement.modal.password }}</label>
            <input v-model="currentUser.password" type="password" placeholder="不修改请留空" />
          </div>
          <div class="ui-g2">
            <div class="ui-field">
              <label>状态</label>
              <select v-model="currentUser.status">
                <option :value="1">正常</option>
                <option :value="0">禁用</option>
              </select>
            </div>
            <div class="ui-field">
              <label>角色（可多选）</label>
              <select v-model="currentUser.role_ids" multiple class="multi">
                <option v-for="role in roles" :key="role.role_id" :value="role.role_id">{{ role.role_name }}</option>
              </select>
            </div>
          </div>
        </div>
        <footer class="ui-modal-f">
          <button class="btn btn-secondary" @click="showEditForm = false">{{ t.common.cancel }}</button>
          <button class="btn btn-primary" @click="updateUser">{{ t.common.save }}</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue'

export default {
  name: 'UserManagement',
  props: {
    t: Object
  },
  setup() {
    const users = ref([])
    const roles = ref([])
    const loading = ref(false)
    // Pagination
    const currentPage = ref(1)
    const pageSize = ref(10) // Fixed to 10 items per page
    const totalUsers = ref(0)
    const totalPages = ref(1)
    
    // 密码强度检测
    const passwordStrength = ref('')
    
    const checkPasswordStrength = (password) => {
      if (password.length < 8 || password.length > 32) {
        passwordStrength.value = 'weak'
        return
      }
      
      let score = 0
      
      if (/[a-z]/.test(password)) score++
      if (/[A-Z]/.test(password)) score++
      if (/[0-9]/.test(password)) score++
      if (/[~!@#$%&*]/.test(password)) score++
      
      const onlyLetters = /^[a-zA-Z]+$/.test(password)
      const onlyNumbers = /^[0-9]+$/.test(password)
      
      if (onlyLetters || onlyNumbers || score <= 1) {
        passwordStrength.value = 'weak'
      } else if (score === 2) {
        passwordStrength.value = 'medium'
      } else if (score === 4) {
        passwordStrength.value = 'strong'
      } else {
        passwordStrength.value = 'medium'
      }
    }
    
    const passwordStrengthText = computed(() => {
      const texts = {
        'weak': '弱',
        'medium': '中',
        'strong': '强'
      }
      return texts[passwordStrength.value] || ''
    })
    
    const passwordStrengthClass = computed(() => {
      const classes = {
        'weak': 'bg-red-200 text-red-800',
        'medium': 'bg-yellow-200 text-yellow-800',
        'strong': 'bg-green-200 text-green-800'
      }
      return classes[passwordStrength.value] || ''
    })
    
    const fetchUsers = async () => {
      loading.value = true
      try {
        console.log('Fetching users...', { page: currentPage.value, size: pageSize.value })
        const { userApi } = await import('../api/index.js')
        const response = await userApi.getList({
          page: currentPage.value,
          size: pageSize.value
        })
        console.log('User fetch response:', response)
        if (response.data.code === 200) {
          users.value = response.data.data.list || []
          totalUsers.value = response.data.data.total || 0
          totalPages.value = response.data.data.totalPages || 1
        }
      } catch (error) {
        console.error('Failed to fetch users:', error)
        alert('获取用户列表失败: ' + (error.message || '未知错误'))
        users.value = []
        totalUsers.value = 0
        totalPages.value = 1
      } finally {
        loading.value = false
      }
    }
    
    const fetchRoles = async () => {
      try {
        console.log('Fetching roles...')
        const { userApi } = await import('../api/index.js')
        const response = await userApi.getRoles()
        console.log('Roles fetch response:', response)
        if (response.data.code === 200) {
          roles.value = response.data.data
        }
      } catch (error) {
        console.error('Failed to fetch roles:', error)
        alert('获取角色列表失败: ' + (error.message || '未知错误'))
      }
    }
    
    const showAddForm = ref(false)
    const showEditForm = ref(false)
    const currentUser = ref({})
    const newUser = ref({ username: '', password: '', email: '', role_ids: [], status: 1 })
    
    const addUser = () => {
      console.log('Add user button clicked')
      newUser.value = { username: '', password: '', email: '', role_ids: [], status: 1 }
      passwordStrength.value = ''
      showAddForm.value = true
    }
    
    const editUser = (user) => {
      console.log('Edit user button clicked:', user)
      currentUser.value = { ...user }
      showEditForm.value = true
    }
    
    const saveNewUser = async () => {
      if (passwordStrength.value === 'weak') {
        alert('密码强度太弱，请包含大小写字母、数字和特殊字符（长度8-32位）')
        return
      }
      try {
        console.log('Saving new user:', newUser.value)
        const { userApi } = await import('../api/index.js')
        const response = await userApi.add(newUser.value)
        console.log('Add user response:', response)
        if (response.data.code === 200) {
          alert('添加成功')
          showAddForm.value = false
          fetchUsers() // 重新获取用户列表
        } else {
          alert('添加失败: ' + (response.data.msg || '未知错误'))
        }
      } catch (error) {
        console.error('Failed to add user:', error)
        alert('添加用户失败: ' + (error.message || '未知错误'))
      }
    }
    
    const updateUser = async () => {
      try {
        console.log('Updating user:', currentUser.value)
        const { userApi } = await import('../api/index.js')
        const response = await userApi.update(currentUser.value)
        console.log('Update user response:', response)
        if (response.data.code === 200) {
          alert('更新成功')
          showEditForm.value = false
          fetchUsers() // 重新获取用户列表
        } else {
          alert('更新失败: ' + (response.data.msg || '未知错误'))
        }
      } catch (error) {
        console.error('Failed to update user:', error)
        alert('更新用户失败: ' + (error.message || '未知错误'))
      }
    }
    
    const deleteUser = async (userId) => {
      console.log('Delete user button clicked:', userId)
      // 实现删除用户的逻辑
      if (confirm('确定要删除这个用户吗？')) {
        try {
          console.log('Deleting user:', userId)
          const { userApi } = await import('../api/index.js')
          const response = await userApi.delete({ user_id: userId })
          console.log('Delete user response:', response)
          if (response.data.code === 200) {
            alert('删除成功')
            fetchUsers() // 重新获取用户列表
          } else {
            alert('删除失败: ' + (response.data.msg || '未知错误'))
          }
        } catch (error) {
          console.error('Failed to delete user:', error)
          alert('删除用户失败: ' + (error.message || '未知错误'))
        }
      }
    }
    
    // Pagination handlers
    const handlePageChange = (page) => {
      currentPage.value = page
      fetchUsers()
    }
    
    const handlePageSizeChange = (size) => {
      pageSize.value = size
      currentPage.value = 1
      fetchUsers()
    }
    
    const goToFirstPage = () => {
      currentPage.value = 1
      fetchUsers()
    }
    
    const goToPreviousPage = () => {
      if (currentPage.value > 1) {
        currentPage.value--
        fetchUsers()
      }
    }
    
    const goToNextPage = () => {
      if (currentPage.value < totalPages.value) {
        currentPage.value++
        fetchUsers()
      }
    }
    
    const goToLastPage = () => {
      currentPage.value = totalPages.value
      fetchUsers()
    }
    
    const goToPage = (e) => {
      const page = parseInt(e.target.value)
      if (!isNaN(page) && page >= 1 && page <= totalPages.value) {
        currentPage.value = page
        fetchUsers()
      }
    }
    
    // 组件挂载时获取用户列表和角色列表
    onMounted(async () => {
      await fetchRoles()
      await fetchUsers()
    })
    
    return {
      users,
      roles,
      loading,
      showAddForm,
      showEditForm,
      newUser,
      currentUser,
      addUser,
      editUser,
      saveNewUser,
      updateUser,
      deleteUser,
      // Password strength
      passwordStrength,
      passwordStrengthText,
      passwordStrengthClass,
      checkPasswordStrength,
      // Pagination
      currentPage,
      pageSize,
      totalUsers,
      totalPages,
      goToPreviousPage,
      goToNextPage
    }
  }
}
</script>

<style scoped>
/* 账号单元格：首字母头像 + 账号名，扫读时比纯文字更快定位 */
.cell-user {
  display: flex;
  align-items: center;
  gap: 9px;
  font-weight: 500;
}

.ava {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 11px;
  font-weight: 600;
  flex: none;
}

.role-tag {
  margin-inline-end: 4px;
}

.row-ops {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  justify-content: flex-end;
}

/* 分页条：贴近表格底部，弱化存在感 */
.pager-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 18px;
  border-top: 1px solid var(--line-2);
}

/* 密码强度：标签与说明分行，避免长句挤在一行 */
.strength-line {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.tip {
  font-size: 11.5px;
  line-height: 1.6;
  margin-top: 5px;
}

.tip.err { color: var(--danger); }
.tip.warn { color: var(--warn-ink); }
.tip.ok { color: var(--ok-ink); }

/* 多选角色列表：默认 select 只有 36px 高，多选需要展开 */
.ui-field select.multi {
  height: auto;
  min-height: 84px;
  padding: 4px;
  background-image: none;
}

.ui-field select.multi option {
  padding: 5px 8px;
  border-radius: var(--r-xs);
}
</style>