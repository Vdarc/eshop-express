<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.profile.title }}</div>
        <div class="sub">账号资料与店铺信息集中在此维护</div>
      </div>
      <div class="acts">
        <button class="btn btn-primary" @click="openEditModal">✎ {{ t.profile.edit }}</button>
      </div>
    </div>

    <!-- 身份卡：头像 + 关键身份信息 -->
    <section class="ui-card id-card">
      <div class="id-body">
        <div class="avatar-wrap">
          <img v-if="!avatarError" :src="getAvatarUrl(avatarUrl)" :alt="username" class="avatar-img" @error="avatarError = true" />
          <div v-else class="avatar-fallback" aria-hidden="true">{{ initial }}</div>
          <div v-if="showEditModal" class="avatar-edit-btn" @click="triggerAvatarUpload">
            <span class="edit-icon">📷</span>
          </div>
        </div>
        <div class="id-meta">
          <div class="id-name">{{ username || '未命名账号' }}</div>
          <div class="id-mail">{{ email || '未绑定邮箱' }}</div>
          <div class="id-tags">
            <span class="ui-tag ui-tag-brand">{{ country || '中国' }}</span>
            <span class="ui-tag ui-tag-mute">{{ gender || '不愿透露' }}</span>
            <span class="ui-tag ui-tag-mute">{{ occupation || '不愿透露' }}</span>
            <span v-if="isMerchant" class="ui-tag ui-tag-ok">商家账号</span>
          </div>
        </div>
        <div v-if="showSuccess" class="save-ok">✓ {{ t.profile.success }}</div>
      </div>
      <input ref="avatarInput" type="file" accept="image/*" class="avatar-input" @change="handleAvatarUpload" />
    </section>

    <!-- 基本信息 -->
    <section class="ui-card">
      <header class="ui-card-h">
        <h3>基本信息</h3>
        <span class="hint">账号相关字段由平台维护</span>
      </header>
      <div class="ui-card-b">
        <div class="kv-grid">
          <div class="kv">
            <span class="k">{{ t.profile.username }}</span>
            <span class="v">{{ username || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.email }}</span>
            <span class="v">{{ email || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.country }}</span>
            <span class="v">{{ country || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.gender }}</span>
            <span class="v">{{ gender || '不愿透露' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.occupation }}</span>
            <span class="v">{{ occupation || '不愿透露' }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 商家店铺信息 -->
    <section v-if="isMerchant" class="ui-card">
      <header class="ui-card-h">
        <h3>{{ t.profile.shopInfo }}</h3>
        <span class="hint">用于买家侧店铺展示与结算</span>
      </header>
      <div class="ui-card-b">
        <div class="kv-grid">
          <div class="kv">
            <span class="k">{{ t.profile.shopName }}</span>
            <span class="v">{{ shopName || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.mainCategory }}</span>
            <span class="v">{{ getCategoryName(mainCategory) || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.contactName }}</span>
            <span class="v">{{ contactName || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.contactPhone }}</span>
            <span class="v mono">{{ contactPhone || '—' }}</span>
          </div>
          <div class="kv">
            <span class="k">{{ t.profile.contactEmail }}</span>
            <span class="v">{{ contactEmail || '—' }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 编辑模态框 -->
    <div class="ui-modal-mask" v-if="showEditModal" @click.self="closeModal">
      <div class="ui-modal">
        <header class="ui-modal-h">
          <h3>{{ t.profile.editProfile }}</h3>
          <button class="x" type="button" aria-label="关闭" @click="closeModal">✕</button>
        </header>
        <form @submit.prevent="saveChanges">
          <div class="ui-modal-b">
            <!-- 头像编辑 -->
            <div class="avatar-edit-group">
              <div class="avatar-edit-container" @click="triggerAvatarUpload">
                <img :src="getAvatarUrl(editForm.avatar)" :alt="editForm.username" class="avatar-edit-img" />
                <div class="avatar-upload-btn"><span>📷 更换头像</span></div>
              </div>
              <input ref="avatarInput" type="file" accept="image/*" class="avatar-input" @change="handleAvatarUpload" />
            </div>

            <div class="ui-g2">
              <div class="ui-field">
                <label>{{ t.profile.username }}</label>
                <input type="text" v-model="editForm.username" />
              </div>
              <div class="ui-field">
                <label>{{ t.profile.email }}</label>
                <input type="email" v-model="editForm.email" />
              </div>
            </div>

            <div class="ui-field">
              <label>{{ t.profile.password }}</label>
              <input type="password" v-model="editForm.newPassword" :placeholder="t.profile.newPasswordPlaceholder" />
              <span class="tip">留空则保持原密码不变</span>
            </div>

            <div class="ui-g2">
              <div class="ui-field">
                <label>{{ t.profile.country }}</label>
                <select v-model="editForm.country">
                  <option v-for="option in countryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </div>
              <div class="ui-field">
                <label>{{ t.profile.gender }}</label>
                <select v-model="editForm.gender">
                  <option v-for="option in genderOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                </select>
              </div>
            </div>

            <div class="ui-field">
              <label>{{ t.profile.occupation }}</label>
              <select v-model="editForm.occupation">
                <option v-for="option in occupationOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>

            <!-- 商家店铺信息 -->
            <template v-if="isMerchant">
              <div class="ui-sec-title shop-sec">{{ t.profile.shopInfo }}</div>
              <div class="ui-g2">
                <div class="ui-field">
                  <label>{{ t.profile.shopName }}</label>
                  <input type="text" v-model="editForm.shopName" />
                </div>
                <div class="ui-field">
                  <label>{{ t.profile.mainCategory }}</label>
                  <select v-model="editForm.mainCategory">
                    <option v-for="(name, key) in t.categories" :key="key" :value="key">{{ name }}</option>
                  </select>
                </div>
                <div class="ui-field">
                  <label>{{ t.profile.contactName }}</label>
                  <input type="text" v-model="editForm.contactName" />
                </div>
                <div class="ui-field">
                  <label>{{ t.profile.contactPhone }}</label>
                  <input type="text" v-model="editForm.contactPhone" />
                </div>
              </div>
              <div class="ui-field">
                <label>{{ t.profile.contactEmail }}</label>
                <input type="email" v-model="editForm.contactEmail" />
              </div>
            </template>
          </div>
          <footer class="ui-modal-f">
            <button type="button" class="btn btn-secondary" @click="closeModal">{{ t.profile.cancel }}</button>
            <button type="submit" class="btn btn-primary">{{ t.profile.save }}</button>
          </footer>
        </form>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue'

export default {
  name: 'Profile',
  props: {
    t: Object
  },
  setup() {
    const username = ref('')
    const email = ref('')
    const avatarUrl = ref('')
    const showSuccess = ref(false)
    // 默认头像文件缺失时不留破图，退化为首字母徽标
    const avatarError = ref(false)
    const isMerchant = ref(false)
    const showEditModal = ref(false)
    const avatarInput = ref(null)

    const shopName = ref('')
    const mainCategory = ref('clothes')
    const contactName = ref('')
    const contactPhone = ref('')
    const contactEmail = ref('')
    
    // 首字母：头像缺失时的兜底显示
    const initial = computed(() => {
      const name = (username.value || '').trim()
      return name ? name.charAt(0).toUpperCase() : '·'
    })

    // 新增字段
    const country = ref('中国')
    const gender = ref('')
    const occupation = ref('')

    // 选项列表 - 中文
    const countryOptionsZh = [
      { label: '沙特', value: '沙特' },
      { label: '阿联酋', value: '阿联酋' },
      { label: '美国', value: '美国' },
      { label: '英国', value: '英国' },
      { label: '法国', value: '法国' },
      { label: '德国', value: '德国' },
      { label: '日本', value: '日本' },
      { label: '韩国', value: '韩国' },
      { label: '中国', value: '中国' },
      { label: '俄罗斯', value: '俄罗斯' },
      { label: '泰国', value: '泰国' },
      { label: '越南', value: '越南' },
      { label: '马来西亚', value: '马来西亚' },
      { label: '印度尼西亚', value: '印尼' },
      { label: '菲律宾', value: '菲律宾' },
      { label: '新加坡', value: '新加坡' }
    ]
    
    // 选项列表 - 英文
    const countryOptionsEn = [
      { label: 'Saudi Arabia', value: '沙特' },
      { label: 'UAE', value: '阿联酋' },
      { label: 'USA', value: '美国' },
      { label: 'UK', value: '英国' },
      { label: 'France', value: '法国' },
      { label: 'Germany', value: '德国' },
      { label: 'Japan', value: '日本' },
      { label: 'Korea', value: '韩国' },
      { label: 'China', value: '中国' },
      { label: 'Russia', value: '俄罗斯' },
      { label: 'Thailand', value: '泰国' },
      { label: 'Vietnam', value: '越南' },
      { label: 'Malaysia', value: '马来西亚' },
      { label: 'Indonesia', value: '印尼' },
      { label: 'Philippines', value: '菲律宾' },
      { label: 'Singapore', value: '新加坡' }
    ]
    
    // 性别选项 - 中文
    const genderOptionsZh = [
      { label: '不愿透露', value: null },
      { label: '男', value: '男' },
      { label: '女', value: '女' }
    ]
    
    // 性别选项 - 英文
    const genderOptionsEn = [
      { label: 'Prefer not to say', value: null },
      { label: 'Male', value: '男' },
      { label: 'Female', value: '女' }
    ]
    
    // 职业选项 - 中文
    const occupationOptionsZh = [
      { label: '不愿透露', value: null },
      { label: '程序员', value: '程序员' },
      { label: '平面设计师', value: '平面设计师' },
      { label: '模特', value: '模特' },
      { label: '户外登山爱好者', value: '户外登山爱好者' },
      { label: '运动员', value: '运动员' },
      { label: '教师', value: '教师' },
      { label: '自由职业者', value: '自由职业者' },
      { label: '企业高管', value: '企业高管' }
    ]
    
    // 职业选项 - 英文
    const occupationOptionsEn = [
      { label: 'Prefer not to say', value: null },
      { label: 'Programmer', value: '程序员' },
      { label: 'Graphic Designer', value: '平面设计师' },
      { label: 'Model', value: '模特' },
      { label: 'Outdoor Enthusiast', value: '户外登山爱好者' },
      { label: 'Athlete', value: '运动员' },
      { label: 'Teacher', value: '教师' },
      { label: 'Freelancer', value: '自由职业者' },
      { label: 'Executive', value: '企业高管' }
    ]
    
    const currentLang = ref(localStorage.getItem('currentLang') || 'zh')
    
    const countryOptions = computed(() => {
      return currentLang.value === 'zh' ? countryOptionsZh : countryOptionsEn
    })
    
    const genderOptions = computed(() => {
      return currentLang.value === 'zh' ? genderOptionsZh : genderOptionsEn
    })
    
    const occupationOptions = computed(() => {
      return currentLang.value === 'zh' ? occupationOptionsZh : occupationOptionsEn
    })
    
    const updateLang = () => {
      currentLang.value = localStorage.getItem('currentLang') || 'zh'
    }

    const editForm = ref({
      username: '',
      email: '',
      avatar: '',
      newPassword: '',
      shopName: '',
      mainCategory: 'clothes',
      contactName: '',
      contactPhone: '',
      contactEmail: '',
      country: '中国',
      gender: '',
      occupation: ''
    })

    onMounted(async () => {
      const storedUsername = localStorage.getItem('loginUsername')
      const storedEmail = localStorage.getItem('loginEmail')
      const storedRole = localStorage.getItem('loginRole')
      const storedUserId = localStorage.getItem('loginUserId')

      console.log('=== Profile mounted ===')
      console.log('Stored username:', storedUsername)
      console.log('Stored email:', storedEmail)
      console.log('Stored role:', storedRole)
      console.log('Stored user ID:', storedUserId)

      if (storedUsername) {
        username.value = storedUsername
      }
      if (storedEmail && storedEmail !== 'undefined' && storedEmail !== 'null') {
        email.value = storedEmail
      }
      if (storedRole === 'merchant') {
        isMerchant.value = true
        console.log('User is a merchant')
      }

      try {
        const { profileApi } = await import('../api/index.js')

        console.log('Fetching user profile')

        const userResponse = await profileApi.getUserInfo()

        console.log('User profile response:', userResponse.data)

        if (userResponse.data.code === 200) {
          const userInfo = userResponse.data.data
          console.log('User info:', userInfo)
          if (userInfo && userInfo.username) {
            username.value = userInfo.username
            localStorage.setItem('loginUsername', userInfo.username)
          }
          if (userInfo && userInfo.email && userInfo.email !== 'undefined' && userInfo.email !== 'null') {
            email.value = userInfo.email
            localStorage.setItem('loginEmail', userInfo.email)
          }
          // 读取新增字段
          if (userInfo && userInfo.country) {
            country.value = userInfo.country
          }
          if (userInfo && userInfo.gender !== undefined) {
            gender.value = userInfo.gender
          }
          if (userInfo && userInfo.occupation !== undefined) {
            occupation.value = userInfo.occupation
          }
          
          // 保存用户信息到 localStorage（用于商品价格换算）
          const userProfile = {
            country: country.value,
            gender: gender.value,
            occupation: occupation.value
          }
          localStorage.setItem('userProfile', JSON.stringify(userProfile))
        }

        if (isMerchant.value) {
          await loadMerchantInfo()
        }
      } catch (error) {
        console.error('Failed to load user info:', error)
        console.error('Error details:', error.response || error.message)
      }
    })

    const loadMerchantInfo = async () => {
      try {
        const { profileApi } = await import('../api/index.js')

        console.log('Loading merchant info')

        const response = await profileApi.getMerchantInfo()

        console.log('Merchant info response:', response.data)

        if (response.data.code === 200) {
          const merchantInfo = response.data.data
          console.log('Merchant info:', merchantInfo)
          shopName.value = merchantInfo.shop_name || ''
          mainCategory.value = merchantInfo.main_category || 'clothes'
          contactName.value = merchantInfo.contact_name || ''
          contactPhone.value = merchantInfo.contact_phone || ''
          contactEmail.value = merchantInfo.contact_email || ''

          console.log('Shop name:', shopName.value)
          console.log('Main category:', mainCategory.value)
          console.log('Contact name:', contactName.value)
          console.log('Contact phone:', contactPhone.value)
          console.log('Contact email:', contactEmail.value)
        }
      } catch (error) {
        console.error('Failed to load merchant info:', error)
        console.error('Error details:', error.response || error.message)
      }
    }

    const openEditModal = () => {
      updateLang()
      editForm.value = {
        username: username.value,
        email: email.value,
        avatar: avatarUrl.value,
        newPassword: '',
        shopName: shopName.value,
        mainCategory: mainCategory.value,
        contactName: contactName.value,
        contactPhone: contactPhone.value,
        contactEmail: contactEmail.value,
        country: country.value,
        gender: gender.value || '',
        occupation: occupation.value || ''
      }
      showEditModal.value = true
    }

    const saveChanges = async () => {
      try {
        const { profileApi } = await import('../api/index.js')

        const userData = {
          username: editForm.value.username,
          email: editForm.value.email,
          country: editForm.value.country,
          gender: editForm.value.gender || null,
          occupation: editForm.value.occupation || null
        }

        if (editForm.value.newPassword) {
          userData.password = editForm.value.newPassword
        }

        await profileApi.updateUserInfo(userData)

        if (isMerchant.value) {
          const merchantData = {
            shop_name: editForm.value.shopName,
            main_category: editForm.value.mainCategory,
            contact_name: editForm.value.contactName,
            contact_phone: editForm.value.contactPhone,
            contact_email: editForm.value.contactEmail
          }

          await profileApi.updateMerchantInfo(merchantData)
        }

        localStorage.setItem('loginUsername', editForm.value.username)
        localStorage.setItem('loginEmail', editForm.value.email)
        
        // 保存用户完整信息到 userProfile（用于商品价格换算）
        const userProfile = {
          country: editForm.value.country,
          gender: editForm.value.gender,
          occupation: editForm.value.occupation
        }
        localStorage.setItem('userProfile', JSON.stringify(userProfile))

        username.value = editForm.value.username
        email.value = editForm.value.email
        country.value = editForm.value.country
        gender.value = editForm.value.gender || ''
        occupation.value = editForm.value.occupation || ''
        if (isMerchant.value) {
          shopName.value = editForm.value.shopName
          mainCategory.value = editForm.value.mainCategory
          contactName.value = editForm.value.contactName
          contactPhone.value = editForm.value.contactPhone
          contactEmail.value = editForm.value.contactEmail
        }

        showEditModal.value = false
        showSuccess.value = true
        setTimeout(() => {
          showSuccess.value = false
        }, 3000)
      } catch (error) {
        console.error('Failed to save changes:', error)
        alert('保存失败: ' + error.message)
      }
    }

    const getCategoryName = (categoryValue) => {
      const categories = {
        'clothes': '服装',
        '3c': '3C电子',
        'home': '家居',
        'beauty': '美妆',
        'baby': '母婴',
        'sports': '运动',
        'car': '汽车'
      }
      return categories[categoryValue] || categoryValue
    }

    const getAvatarUrl = (url) => {
      const protocol = window.location.protocol
      const baseUrl = window.location.host
      if (!url) {
        return `${protocol}//${baseUrl}/uploads/avatar/DefaultAvatar.png`
      }
      if (url.startsWith('http')) {
        return url
      }
      return `${protocol}//${baseUrl}${url}`
    }

    const triggerAvatarUpload = () => {
      avatarInput.value.click()
    }

    const handleAvatarUpload = async (event) => {
      const file = event.target.files[0]
      if (!file) return

      try {
        const formData = new FormData()
        formData.append('avatar', file)

        const response = await fetch('/api/user/upload-avatar', {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('jwt_token')}`
          },
          body: formData
        })

        const result = await response.json()
        if (result.code === 200) {
          avatarUrl.value = result.data.avatar_url
          editForm.value.avatar = result.data.avatar_url
          await updateAvatarInProfile(avatarUrl.value)
          showSuccess.value = true
          setTimeout(() => {
            showSuccess.value = false
          }, 3000)
        } else {
          alert('上传失败: ' + result.error)
        }
      } catch (error) {
        console.error('头像上传失败:', error)
        alert('头像上传失败: ' + error.message)
      }
    }

    const updateAvatarInProfile = async (avatarUrl) => {
      try {
        const { profileApi } = await import('../api/index.js')
        await profileApi.updateUserInfo({ avatar_url: avatarUrl })
      } catch (error) {
        console.error('更新头像失败:', error)
      }
    }

    const closeModal = () => {
      showEditModal.value = false
    }

    return {
      username,
      email,
      avatarUrl,
      showSuccess,
      avatarError,
      initial,
      isMerchant,
      shopName,
      mainCategory,
      contactName,
      contactPhone,
      contactEmail,
      country,
      gender,
      occupation,
      countryOptions,
      genderOptions,
      occupationOptions,
      showEditModal,
      editForm,
      avatarInput,
      openEditModal,
      closeModal,
      saveChanges,
      getCategoryName,
      getAvatarUrl,
      triggerAvatarUpload,
      handleAvatarUpload,
      updateLang
    }
  }
}
</script>

<style scoped>
/* 身份卡：头像 + 名称 + 标签，横向排列 */
.id-body {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 22px;
  flex-wrap: wrap;
}

.avatar-wrap {
  position: relative;
  width: 68px;
  height: 68px;
  flex: none;
}

.avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--line);
  box-shadow: 0 0 0 4px var(--bg-soft);
  background: var(--bg-sink);
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(150deg, #5f7ff0, #3b5bdb);
  color: #fff;
  font-size: 24px;
  font-weight: 600;
  box-shadow: 0 0 0 4px var(--bg-soft);
}

.avatar-edit-btn {
  position: absolute;
  inset-inline-end: -2px;
  bottom: -2px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 11px;
  cursor: pointer;
  border: 2px solid #fff;
  transition: transform var(--t-fast) var(--ease-out);
}

.avatar-edit-btn:hover {
  transform: scale(1.08);
}

.avatar-input {
  display: none;
}

.id-meta {
  min-width: 0;
  flex: 1;
}

.id-name {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: -.02em;
  line-height: 1.4;
}

.id-mail {
  font-size: 12.5px;
  color: var(--ink-3);
  margin-top: 2px;
  word-break: break-all;
}

.id-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 9px;
}

.save-ok {
  margin-inline-start: auto;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--ok-ink);
  background: var(--ok-soft);
  border: 1px solid var(--ok-line);
  border-radius: var(--r-pill);
  padding: 4px 12px;
  animation: pop-in var(--t-base) var(--ease-spring) both;
}

/* 字段网格：标签在左、值在右，比整排只读输入框更省空间也更好读 */
.kv-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: #fff;
}

/* 用单元格自身描边代替 1px 间隙：字段数为奇数时，最后一格右侧留白不再是灰块 */
.kv {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 11px 14px;
  background: #fff;
  min-width: 0;
  border-bottom: 1px solid var(--line-2);
}

.kv:nth-child(odd) {
  border-inline-end: 1px solid var(--line-2);
}

.kv:nth-last-child(-n+2) {
  border-bottom: 0;
}

.kv .k {
  width: 92px;
  flex: none;
  font-size: 12px;
  color: var(--ink-3);
}

.kv .v {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--ink);
  font-weight: 500;
  word-break: break-word;
}

/* 编辑弹窗内的头像区 */
.avatar-edit-group {
  text-align: center;
  margin-bottom: 18px;
}

.avatar-edit-container {
  position: relative;
  width: 88px;
  height: 88px;
  margin: 0 auto;
  cursor: pointer;
}

.avatar-edit-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--line);
}

.avatar-upload-btn {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(20, 24, 33, .5);
  color: #fff;
  font-size: 11px;
  opacity: 0;
  transition: opacity var(--t-fast) var(--ease-out);
}

.avatar-edit-container:hover .avatar-upload-btn {
  opacity: 1;
}

.shop-sec {
  margin: 20px 0 12px;
  padding-top: 16px;
  border-top: 1px solid var(--line-2);
}

@media (max-width: 768px) {
  .kv-grid {
    grid-template-columns: 1fr;
  }

  .id-body {
    gap: 14px;
    padding: 16px;
  }

  .kv:nth-child(odd) {
    border-inline-end: 0;
  }

  .kv .k {
    width: 80px;
  }
}
</style>
