<template>
  <div class="max-w-md mx-auto card">
    <h2 class="text-xl font-bold mb-4">{{ t.register.title }}</h2>
    
    <!-- 注册类型选择 -->
    <div class="mb-4">
      <label class="block mb-2">{{ t.register.registerType }}</label>
      <div class="flex flex-col space-y-2">
        <label class="flex items-center">
          <input type="radio" v-model="registerType" value="customer" class="mr-3" style="width: 16px; height: 16px;"/>
          {{ t.register.customer }}
        </label>
        <label class="flex items-center">
          <input type="radio" v-model="registerType" value="merchant" class="mr-3" style="width: 16px; height: 16px;"/>
          {{ t.register.merchant }}
        </label>
      </div>
    </div>
    
    <!-- 普通用户注册表单 -->
    <div v-if="registerType === 'customer'">
      <input v-model="regForm.username" :placeholder="t.register.username" class="mb-3"/>
      <input v-model="regForm.email" type="email" :placeholder="t.register.email" class="mb-3"/>
      <input 
        v-model="regForm.password" 
        type="password" 
        :placeholder="t.register.password" 
        class="mb-2"
        @input="checkPasswordStrength(regForm.password)"
      />
      <!-- 密码强度提示：未输入时不占位 -->
      <div class="mb-4" v-if="passwordStrength">
        <div class="flex items-center mb-1">
          <span class="text-sm font-medium text-gray-600">{{ t.register.passwordStrength }}:</span>
          <span 
            :class="[
              'ml-2 px-2 py-0.5 rounded text-xs font-medium',
              passwordStrengthClass
            ]"
          >
            {{ passwordStrengthText }}
          </span>
        </div>
        <div v-if="passwordStrength === 'weak'" class="text-red-500 text-sm mt-1">
          {{ t.register.passwordWeak }}
        </div>
        <div v-else-if="passwordStrength === 'medium'" class="text-yellow-500 text-sm mt-1">
          {{ t.register.passwordMedium }}
        </div>
        <div v-else-if="passwordStrength === 'strong'" class="text-green-500 text-sm mt-1">
          {{ t.register.passwordStrong }}
        </div>
        <div v-if="!passwordStrength && regForm.password" class="text-gray-400 text-xs mt-1">
          {{ t.register.passwordLength }}
        </div>
      </div>
      <button 
        @click="handleRegister" 
        :disabled="loading.reg || passwordStrength === 'weak'" 
        class="btn btn-primary w-full mb-2"
      >
        <span v-if="loading.reg" class="loader"></span>
        {{ t.register.button }}
      </button>
    </div>
    
    <!-- 商家注册表单 -->
    <div v-else-if="registerType === 'merchant'">
      <input v-model="merchantRegForm.username" :placeholder="t.register.username" class="mb-3"/>
      <input v-model="merchantRegForm.email" type="email" :placeholder="t.register.email" class="mb-3"/>
      <input 
        v-model="merchantRegForm.password" 
        type="password" 
        :placeholder="t.register.password" 
        class="mb-2"
        @input="checkPasswordStrength(merchantRegForm.password)"
      />
      <!-- 密码强度提示：未输入时不占位 -->
      <div class="mb-3" v-if="passwordStrength">
        <div class="flex items-center mb-1">
          <span class="text-sm font-medium text-gray-600">{{ t.register.passwordStrength }}:</span>
          <span 
            :class="[
              'ml-2 px-2 py-0.5 rounded text-xs font-medium',
              passwordStrengthClass
            ]"
          >
            {{ passwordStrengthText }}
          </span>
        </div>
        <div v-if="passwordStrength === 'weak'" class="text-red-500 text-sm mt-1">
          {{ t.register.passwordWeak }}
        </div>
        <div v-else-if="passwordStrength === 'medium'" class="text-yellow-500 text-sm mt-1">
          {{ t.register.passwordMedium }}
        </div>
        <div v-else-if="passwordStrength === 'strong'" class="text-green-500 text-sm mt-1">
          {{ t.register.passwordStrong }}
        </div>
        <div v-if="!passwordStrength && merchantRegForm.password" class="text-gray-400 text-xs mt-1">
          {{ t.register.passwordLength }}
        </div>
      </div>
      <input v-model="merchantRegForm.shop_name" :placeholder="t.register.shopName" class="mb-3"/>
      <select v-model="merchantRegForm.main_category" class="mb-3">
        <option value="clothes">{{ t.categories.clothes }}</option>
        <option value="3c">{{ t.categories['3c'] }}</option>
        <option value="home">{{ t.categories.home }}</option>
        <option value="beauty">{{ t.categories.beauty }}</option>
        <option value="baby">{{ t.categories.baby }}</option>
        <option value="sports">{{ t.categories.sports }}</option>
        <option value="car">{{ t.categories.car }}</option>
      </select>
      <input v-model="merchantRegForm.contact_name" :placeholder="t.register.contactName" class="mb-3"/>
      <input v-model="merchantRegForm.contact_phone" :placeholder="t.register.contactPhone" class="mb-3"/>
      <input v-model="merchantRegForm.contact_email" type="email" :placeholder="t.register.contactEmail" class="mb-4"/>
      <button 
        @click="handleRegisterMerchant" 
        :disabled="loading.reg || passwordStrength === 'weak'" 
        class="btn btn-primary w-full mb-2"
      >
        <span v-if="loading.reg" class="loader"></span>
        {{ t.register.merchantButton }}
      </button>
    </div>
    
    <button @click="changeRoute('login')" class="btn btn-secondary w-full">
      {{ t.register.toLogin }}
    </button>
  </div>
</template>

<script>
import { ref, computed } from 'vue'

export default {
  name: 'Register',
  props: {
    t: Object,
    regForm: Object,
    merchantRegForm: Object,
    loading: Object,
    changeRoute: Function,
    register: Function,
    registerMerchant: Function
  },
  setup(props) {
    const registerType = ref('customer')
    const passwordStrength = ref('')
    
    // 密码强度校验函数
    const checkPasswordStrength = (password) => {
      // 密码长度校验
      if (password.length < 8 || password.length > 32) {
        passwordStrength.value = 'weak'
        return
      }
      
      let score = 0
      
      // 检查是否包含小写字母
      if (/[a-z]/.test(password)) score++
      // 检查是否包含大写字母
      if (/[A-Z]/.test(password)) score++
      // 检查是否包含数字
      if (/[0-9]/.test(password)) score++
      // 检查是否包含特殊字符
      if (/[~!@#$%&*]/.test(password)) score++
      
      // 判断强度：仅字母或仅数字属于弱密码
      const onlyLetters = /^[a-zA-Z]+$/.test(password)
      const onlyNumbers = /^[0-9]+$/.test(password)
      
      if (onlyLetters || onlyNumbers || score <= 1) {
        passwordStrength.value = 'weak'
      } else if (score === 2) {
        // 字母 + 数字为中等强度
        passwordStrength.value = 'medium'
      } else if (score === 4) {
        // 大小写字母 + 数字 + 特殊符号为强密码
        passwordStrength.value = 'strong'
      } else {
        // 其他情况为中等强度
        passwordStrength.value = 'medium'
      }
    }
    
    // 密码强度文本
    const passwordStrengthText = computed(() => {
      const texts = {
        'weak': props.t.register.weak,
        'medium': props.t.register.medium,
        'strong': props.t.register.strong
      }
      return texts[passwordStrength.value] || ''
    })
    
    // 密码强度样式类
    const passwordStrengthClass = computed(() => {
      const classes = {
        'weak': 'bg-red-200 text-red-800',
        'medium': 'bg-yellow-200 text-yellow-800',
        'strong': 'bg-green-200 text-green-800'
      }
      return classes[passwordStrength.value] || ''
    })
    
    // 处理普通用户注册
    const handleRegister = () => {
      if (passwordStrength.value === 'weak') {
        alert(props.t.register.passwordWeak)
        return
      }
      props.register()
    }
    
    // 处理商家注册
    const handleRegisterMerchant = () => {
      if (passwordStrength.value === 'weak') {
        alert(props.t.register.passwordWeak)
        return
      }
      props.registerMerchant()
    }
    
    return {
      registerType,
      passwordStrength,
      passwordStrengthText,
      passwordStrengthClass,
      checkPasswordStrength,
      handleRegister,
      handleRegisterMerchant,
      ...props
    }
  }
}
</script>