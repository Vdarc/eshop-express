<template>
  <div class="app-shell" :class="{ 'drawer-open': sidebarOpen }">
    <!-- 移动端抽屉遮罩 -->
    <div v-if="sidebarOpen" class="side-mask" @click="sidebarOpen = false"></div>

    <!-- 侧栏：按角色分组，选中态由 currentView 决定（原型 .side 212px / #141821） -->
    <AppSidebar
      :groups="menuGroups"
      :active="currentView"
      :open="sidebarOpen"
      :footer="sidebarFooter"
      @navigate="onNavigate"
    />

    <div class="main">
      <!-- 顶栏：标题 + 面包屑 + 目标市场 + 界面语言 + 头像（原型 .top 54px） -->
      <header class="top">
        <button
          type="button"
          class="top-menu-btn"
          aria-label="展开菜单"
          @click="sidebarOpen = !sidebarOpen"
        >
          ☰
        </button>
        <h1 class="top-title">{{ currentViewTitle }}</h1>
        <span class="top-crumb">{{ breadcrumb }}</span>
        <div class="top-right">
          <!-- 界面语言只有 3 档（中/英/阿）；商品字段存 13 个语种，两者不是一回事。
               原这里还有一个「界面语言 · 中文」标签，与右侧切换器信息重复，已去掉。 -->
          <LangSwitch :model-value="lang" @change="setLang" />
          <div v-if="isLoggedIn" class="user-menu-wrap">
            <button
              type="button"
              class="avatar"
              :title="currentUser?.username"
              aria-haspopup="menu"
              :aria-expanded="userMenuOpen ? 'true' : 'false'"
              @click.stop="toggleUserMenu"
            >
              <img v-if="avatarUrl" :src="avatarUrl" :alt="currentUser?.username" />
              <span v-else>{{ avatarInitial }}</span>
            </button>
            <div v-if="userMenuOpen" class="user-menu" role="menu">
              <div class="user-menu-head">
                <b>{{ currentUser?.username }}</b>
                <span v-if="currentUser?.email">{{ currentUser.email }}</span>
              </div>
              <button type="button" class="user-menu-item" role="menuitem" @click="openProfileFromMenu">
                {{ t.nav.profile }}
              </button>
              <div class="user-menu-sep"></div>
              <button type="button" class="user-menu-item is-danger" role="menuitem" @click="handleLogout">
                {{ t.nav.logout }}
              </button>
            </div>
          </div>
        </div>
      </header>

      <main class="content-wrap" :class="{ 'is-auth': !isLoggedIn }">
        <!-- 动态组件加载（class 透传到组件根节点，用于切换页面的入场动效） -->
        <component :is="currentComponent" 
                   class="view-fade"
                   :t="t"
                   :login-form="loginForm"
                   :reg-form="regForm"
                   :merchant-reg-form="merchantRegForm"
                   :form="form"
                   :loading="loading"
                   :loading-translate="loadingTranslate"
                   :show-results="showResults"
                   :translation-result="translationResult"
                   :selected-version="selectedVersion"
                   :expanded-cards="expandedCards"
                   :uploaded-file-name="uploadedFileName"
                   :is-text-inputting="isTextInputting"
                   :change-route="changeRoute"
                   :login="login"
                   :register="register"
                   :register-merchant="registerMerchant"
                   :translate="translate"
                   :remove-uploaded-file="removeUploadedFile"
                   :handle-text-input="handleTextInput"
                   :select-version="selectVersion"
                   :toggle-card="toggleCard"
                   :handle-export="handleExport"
                   @file-selected="handleFileSelected"
                   @update:lang="setLang"
                   :lang="lang"
                   :product-id="currentView === 'productDetail' ? currentViewParams.esin : null"
                   :current-view-params="currentViewParams"
        />
        <!-- 兜底：只有未登记到 componentMap 的视图才走这里 -->
        <div v-if="!knownViews.includes(currentView)" class="ui-card">
          <div class="ui-card-b">
            <h2 class="text-2xl font-bold mb-2">{{ currentViewTitle }}</h2>
            <p class="ui-sub">该页面尚未实现</p>
          </div>
        </div>
      </main>
    </div>

    <!-- AI 客服助手（RAG）：登录后右下角浮动入口 -->
    <button
      v-if="isLoggedIn"
      class="assistant-fab"
      :class="{ 'assistant-fab-active': showAssistant }"
      :title="(t.aiAssistant && t.aiAssistant.title) || 'AI 客服助手'"
      @click="showAssistant = !showAssistant"
    >
      <svg v-if="!showAssistant" viewBox="0 0 24 24" width="26" height="26" fill="none"
           stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 11.5a8.4 8.4 0 01-8.9 8.4 9 9 0 01-3.5-.8L3 21l1.9-5.1a8.3 8.3 0 01-1-4A8.4 8.4 0 0112.4 3 8.4 8.4 0 0121 11.5z" />
        <circle cx="8.8" cy="11.5" r="1.05" fill="currentColor" stroke="none"/>
        <circle cx="12.4" cy="11.5" r="1.05" fill="currentColor" stroke="none"/>
        <circle cx="16" cy="11.5" r="1.05" fill="currentColor" stroke="none"/>
      </svg>
      <svg v-else viewBox="0 0 24 24" width="22" height="22" fill="none"
           stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>

    <div v-if="isLoggedIn && showAssistant" class="assistant-panel">
      <AIAssistant :t="t" @close="showAssistant = false" />
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
// 传统导入组件
import Login from './components/Login.vue'
import Register from './components/Register.vue'
import Translate from './components/Translate.vue'
import TranslateHistory from './components/TranslateHistory.vue'
import Recommend from './components/Recommend.vue'
import Profile from './components/Profile.vue'
import Glossary from './components/Glossary.vue'
import ProductListing from './components/ProductListing.vue'

import UserManagement from './components/UserManagement.vue'
import LogManagement from './components/LogManagement.vue'
import Dashboard from './components/Dashboard.vue'
import DataAnalysis from './components/DataAnalysis.vue'
import ProductDetail from './components/ProductDetail.vue'
import SearchResults from './components/SearchResults.vue'
import MerchantApproval from './components/MerchantApproval.vue'
import ProductPublish from './components/ProductPublish.vue'
import MerchantProductManagement from './components/MerchantProductManagement.vue'
import AIAssistant from './components/AIAssistant.vue'
import TermFeedback from './components/TermFeedback.vue'
// 布局与 UI 组件层
import AppSidebar from './components/layout/AppSidebar.vue'
import LangSwitch from './components/ui/LangSwitch.vue'
// 按需导入axios
const axios = () => import('axios')

export default {
  name: 'App',
  components: {
    Login,
    Register,
    Translate,
    TranslateHistory,
    Recommend,
    Profile,
    Glossary,
    UserManagement,
    LogManagement,
    Dashboard,
    DataAnalysis,
    ProductDetail,
    SearchResults,
    MerchantApproval,
    ProductListing,
    ProductPublish,
    MerchantProductManagement,
    AIAssistant,
    TermFeedback,
    AppSidebar,
    LangSwitch
  },
  setup() {
    // 状态管理
    const currentView = ref('login')
    // AI 客服浮层开关；支持 ?assistant=1 直接展开（便于演示/截图）
    const showAssistant = ref(
      new URLSearchParams(window.location.search).get('assistant') === '1'
    )

    const currentViewParams = ref({})
    const isLoggedIn = ref(false)
    const userMenus = ref([])
    const roleKeys = ref([])
    // 界面语言只有 3 档：zh / en / ar（商品字段有 13 个语种，两者不是一回事）
    const lang = ref(localStorage.getItem('lang') || 'zh')
    const currentUser = ref(null)
    // 移动端侧栏抽屉
    const sidebarOpen = ref(false)
    // 买家侧视图：顶栏语言切换同时决定商品标题的展示语言
    const BUYER_VIEWS = ['productListing', 'searchResults', 'productDetail']
    
    // 表单数据
    const loginForm = ref({ username: '', password: '' })
    const regForm = ref({ username: '', email: '', password: '' })
    const merchantRegForm = ref({ 
      username: '', 
      email: '', 
      password: '',
      shop_name: '',
      main_category: 'clothes',
      contact_name: '',
      contact_phone: '',
      contact_email: ''
    })
    const form = ref({ text: '', target_lang: 'en', category: 'clothes', country: 'saudi' })
    
    // 加载状态
    const loading = ref({ login: false, reg: false, reco: false, glossary: false, publish: false })
    const loadingTranslate = ref(false)
    
    // 翻译相关
    const showResults = ref(false)
    const translationResult = ref({ versionA: '', versionB: '', core_selling_points: [], cultural_notes: '', seo_keywords: [] })
    const selectedVersion = ref('A')
    const expandedCards = ref({ selling: false })
    
    // 文件上传
    const uploadedFileName = ref('')
    const isTextInputting = ref(false)
    
    // 动态组件计算属性
    const currentComponent = computed(() => {
      const componentMap = {
        login: 'Login',
        register: 'Register',
        translate: 'Translate',
        translateHistory: 'TranslateHistory',
        recommend: 'Recommend',
        profile: 'Profile',
        glossary: 'Glossary',
        'system/user': 'UserManagement',
        'system/log': 'LogManagement',
        'system/merchant': 'MerchantApproval',
        dashboard: 'Dashboard',
        dataAnalysis: 'DataAnalysis',
        productDetail: 'ProductDetail',
        productListing: 'ProductListing',
        searchResults: 'SearchResults',
        productPublish: 'ProductPublish',
        merchantProducts: 'MerchantProductManagement',
        termFeedback: 'TermFeedback'
      }
      return componentMap[currentView.value] || 'Login'
    })
    
    // 多语言
    const messages = {
      zh: {
        system: { title: "跨境电商翻译与本地化推荐", logManagement: "日志管理", operation: "操作", user: "用户", ip: "IP", time: "时间", details: "详情" },
        nav: {
          login: '登录', register: '注册', translate: '智能翻译',
          translateHistory: '翻译历史', recommend: '推荐系统',
          profile: '个人中心', logout: '退出', account: '账号管理',
          log: '日志管理', glossary: '术语库', productPublish: '商品发布',
          'system/user': '账号管理',
          'system/merchant': '商家审核',
          'system/log': '日志管理',
          dashboard: '运营仪表盘',
          merchantProducts: '商品管理',
          productListing: '商品列表'
        },
        login: {
          title: '用户登录', username: '用户名', password: '密码',
          button: '登录', toRegister: '没有账号？去注册'
        },
        register: {
          title: '用户注册', username: '用户名', email: '邮箱', password: '密码',
          button: '注册', toLogin: '已有账号？去登录',
          registerType: '注册类型', customer: '普通用户', merchant: '商家用户',
          shopName: '店铺名称', contactName: '联系人姓名', contactPhone: '联系电话', contactEmail: '联系邮箱',
          merchantButton: '注册商家',
          passwordStrength: '密码强度', weak: '弱', medium: '中等', strong: '强',
          passwordWeak: '⚠️ 密码强度太弱，请包含大小写字母、数字和特殊字符（长度8-32位）',
          passwordMedium: '⚡ 建议添加特殊字符以提高密码强度',
          passwordStrong: '✅ 密码强度良好', passwordLength: '请输入8-32位密码'
        },
        translate: {
          title: '智能电商翻译', sourceFile: '上传文件（TXT/DOCX）',
          uploadText: '点击或拖拽文件到此处', uploadHint: '支持 .txt / .docx 格式，自动解析并填充文本',
          orText: '或直接粘贴文本', placeholder: '请输入要翻译的商品标题或描述...',
          button: '开始翻译', loading: '翻译中...', results: '翻译结果',
          versionA: '版本A (标准)', versionB: '版本B (文化适配)',
          tagDirect: '精简直接', tagLocalized: '本地化程度高',
          coreSellingPoints: '核心卖点', seoKeywords: 'SEO关键词建议',
          culturalNotes: '文化敏感点提示', targetLang: '目标语言',
          category: '商品类目', country: '目标国家'
        },
        export: { txt: '导出 TXT', excel: '导出 Excel' },
        languages: {
          zh: '中文', en: '英语', ar: '阿拉伯语', fr: '法语',
          de: '德语', ja: '日语', ko: '韩语', ru: '俄语',
          th: '泰语', vi: '越南语', ms: '马来语', id: '印尼语',
          tl: '菲律宾语'
        },
        categories: {
          clothes: '服装', '3c': '3C电子', home: '家居用品',
          beauty: '美妆护肤', baby: '母婴用品', sports: '运动户外',
          car: '汽车用品', all: '通用'
        },
        dashboard: {
          title: '运营仪表盘',
          totalUsers: '总用户数',
          totalTranslations: '总翻译量',
          totalProducts: '总商品数',
          totalRevenue: '总收入',
          fromLastMonth: '较上月',
          translationTrend: '翻译趋势',
          recommendationSystem: '推荐系统'
        },
        countries: {
          saudi: '沙特阿拉伯', uae: '阿联酋', usa: '美国',
          uk: '英国', france: '法国', germany: '德国',
          japan: '日本', korea: '韩国', china: '中国',
          russia: '俄罗斯', thailand: '泰国', vietnam: '越南',
          malaysia: '马来西亚', indonesia: '印度尼西亚',
          philippines: '菲律宾', singapore: '新加坡'
        },
        publish: {
          title: '商品发布',
          basicInfo: '基本信息',
          titleLabel: '商品标题',
          titlePlaceholder: '请输入商品标题',
          categoryLabel: '商品类目',
          descriptionLabel: '商品描述',
          descriptionPlaceholder: '请输入详细的商品描述',
          keywordsLabel: '关键词',
          keywordsPlaceholder: '请输入商品关键词，用逗号分隔',
          publishSettings: '发布设置',
          targetCountryLabel: '目标国家',
          targetLanguagesLabel: '目标语言（可多选）',
          targetPlatformsLabel: '目标平台（可多选）',
          publishButton: '发布商品',
          publishResult: '发布结果',
          productId: '商品ID',
          translationCount: '翻译语言数',
          complianceCount: '合规检查平台数',
          publishAnother: '发布另一个商品'
        },
        common: {
          noData: '暂无数据', statusActive: '正常', statusInactive: '禁用',
          cancel: '取消', save: '保存', refresh: '刷新数据', none: '无',
          previousPage: '上一页', nextPage: '下一页'
        },
        system: {
          logManagement: '日志管理',
          operation: '操作',
          user: '用户',
          ip: 'IP',
          time: '时间',
          details: '详情'
        },
        merchantApproval: {
          title: '商家审核',
          pendingApplications: '待审核申请',
          shopName: '店铺名称',
          username: '用户名',
          email: '邮箱',
          mainCategory: '主营类目',
          contactName: '联系人姓名',
          contactPhone: '联系电话',
          actions: '操作',
          approve: '批准',
          reject: '拒绝',
          applications: '个申请'
        },
        userManagement: {
          title: '账号管理', addUser: '新增用户',
          table: {
            username: '账号', role: '角色', status: '状态',
            createTime: '创建时间', actions: '操作'
          },
          actions: { edit: '编辑', delete: '删除' },
          modal: {
            addTitle: '新增用户', editTitle: '编辑用户',
            username: '账号', password: '密码（编辑可不填）'
          }
        },
        dataAnalysis: {
          title: '数据分析与效果报表',
          recommendStats: '本地化推荐统计',
          translateStats: '智能翻译统计',
          selectDate: '选择日期',
          refresh: '刷新数据',
          dailyExposure: '当日总曝光数',
          exposureDescription: '推荐商品展示次数',
          dailyClicks: '当日总点击数',
          clicksDescription: '用户点击商品次数',
          clickRate: '当日点击率',
          clickRateDescription: '点击数 / 曝光数',
          topProducts: '商品点击排行 Top10',
          rank: '排名',
          productId: '商品ID',
          clickCount: '点击次数',
          costSavingSummary: '成本节省汇总（本月）',
          totalChars: '累计翻译字符数',
          manualCost: '人工翻译成本（估算）',
          savedCost: '实际节省成本',
          costSavingDescription: '本月通过智能翻译，累计处理 {total_chars} 字符，相当于节省了 ¥{saved_cost} 人工翻译费用',
          todayTranslateData: '当日翻译数据',
          charCount: '翻译字符数',
          apiCost: 'API调用成本',
          monthlyData: '本月累计数据',
          totalApiCost: 'API总成本',
          costSavingRate: '成本节省率',
          translateVolumeTrend: '最近30天翻译量趋势',
          apiCostTrend: '最近30天API成本趋势'
        },
        recommend: {
          title: '智能推荐',
          noResults: '暂无推荐结果',
          productId: 'ID',
          origin: '产地',
          country: '目标国家',
          category: '商品类别',
          searchPlaceholder: '搜索商品...',
          searchButton: '搜索',
          loading: '加载中...',
          recommended: '猜你喜欢',
          hotProducts: '热门商品',
          allProducts: '所有商品',
          resultsCount: '共找到',
          items: '件商品',
          categories: '商品分类',
          allCategories: '全部',
          priceRange: '价格范围',
          allPrices: '全部',
          below100: '100元以下',
          between100500: '100-500元',
          above500: '500元以上',
          noResultsTitle: '未找到相关商品',
          noResultsMessage: '请尝试其他关键词',
          backToHome: '返回首页',
          previous: '上一页',
          page: '第',
          of: '页，共',
          next: '下一页'
        },
        profile: {
          title: '个人中心',
          username: '用户名',
          email: '邮箱',
          avatar: '头像',
          password: '密码',
          newPasswordPlaceholder: '输入新密码',
          save: '保存修改',
          edit: '编辑',
          editProfile: '编辑个人资料',
          cancel: '取消',
          success: '保存成功！',
          shopInfo: '店铺信息',
          shopName: '店铺名称',
          mainCategory: '主营类目',
          contactName: '联系人姓名',
          contactPhone: '联系电话',
          contactEmail: '联系邮箱',
          country: '国家',
          gender: '性别',
          occupation: '职业'
        },
        productDetail: {
          title: '商品详情',
          loading: '加载中...',
          home: '首页',
          searchResults: '搜索结果',
          product: '商品',
          productId: '商品ID',
          origin: '产地',
          category: '类别',
          productDescription: '商品描述',
          noDescription: '暂无描述',
          quantity: '数量',
          buyNow: '立即购买',
          productDetails: '商品详情',
          noDetails: '暂无详细信息',
          purchaseSuccess: '购买成功',
          orderSubmitted: '您的订单已成功提交！',
          confirm: '确定',
          productImage: '商品图片',
          rating: '评分',
          defaultCountry: '中国'
        },
        productListing: {
          title: '商品列表'
        },
        searchResults: {
          title: '搜索结果'
        },
        productPublish: {
          title: '商品发布'
        },
        merchantProducts: {
          title: '我的商品管理',
          searchPlaceholder: '搜索商品',
          searchButton: '搜索',
          editButton: '编辑',
          unpublishButton: '下架',
          loading: '加载中...',
          noProducts: '暂无商品',
          editModalTitle: '编辑商品',
          titleChinese: '商品标题（中文）',
          titleForeign: '商品标题（{language}）',
          descChinese: '商品描述（中文）',
          descForeign: '商品描述（{language}）',
          categoryLabel: '商品分类',
          cancelButton: '取消',
          saveButton: '保存',
          categories: {
            clothes: '服装',
            '3c': '3C电子产品',
            home: '家居用品',
            beauty: '美妆',
            baby: '母婴',
            sports: '运动',
            car: '汽车用品'
          }
        },
        glossary: {
          title: '术语库管理',
          search: '搜索术语',
          addTerm: '添加术语',
          chineseTerm: '中文术语',
          foreignTerm: '外语术语',
          language: '语言',
          category: '分类',
          note: '备注',
          actions: '操作',
          editTitle: '编辑术语',
          addTitle: '添加术语',
          save: '保存',
          cancel: '取消'
        },
        translationHistory: {
          title: '翻译历史记录',
          totalRecords: '共 {total} 条记录',
          previousPage: '上一页',
          nextPage: '下一页',
          noRecords: '暂无翻译历史记录',
          originalText: '原文',
          targetLanguage: '目标语言',
          translationTime: '翻译时间',
          versionA: '版本A（标准翻译）',
          versionB: '版本B（文化适配）'
        },

      },
      en: {
        system: { title: "Cross-border E-commerce Translation and Localization", logManagement: "Log Management", operation: "Operation", user: "User", ip: "IP", time: "Time", details: "Details" },
        nav: {
          login: 'Login', register: 'Register', translate: 'Smart Translation',
          translateHistory: 'Translation History', recommend: 'Recommendation System',
          profile: 'Personal Center', logout: 'Logout', account: 'Account Management',
          log: 'Log Management', glossary: 'Glossary',
          productPublish: 'Product Publish', merchantProducts: 'Product Management',
          productListing: 'Product List',
          'system/user': 'Account Management',
          'system/merchant': 'Merchant Review',
          'system/log': 'Log Management',
          dashboard: 'Operation Dashboard'
        },
        login: {
          title: 'User Login', username: 'Username', password: 'Password',
          button: 'Login', toRegister: 'No account? Go to register'
        },
        register: {
          title: 'User Registration', username: 'Username', email: 'Email', password: 'Password',
          button: 'Register', toLogin: 'Already have an account? Go to login',
          registerType: 'Registration Type', customer: 'Customer', merchant: 'Merchant',
          shopName: 'Shop Name', contactName: 'Contact Name', contactPhone: 'Contact Phone', contactEmail: 'Contact Email',
          merchantButton: 'Register as Merchant',
          passwordStrength: 'Password Strength', weak: 'Weak', medium: 'Medium', strong: 'Strong',
          passwordWeak: '⚠️ Password too weak. Please include uppercase letters, lowercase letters, numbers and special characters (8-32 characters)',
          passwordMedium: '⚡ Consider adding special characters to increase password strength',
          passwordStrong: '✅ Password strength is good', passwordLength: 'Please enter 8-32 characters'
        },
        translate: {
          title: 'Smart E-commerce Translation', sourceFile: 'Upload File (TXT/DOCX)',
          uploadText: 'Click or drag file here', uploadHint: 'Support .txt / .docx format, automatically parse and fill text',
          orText: 'Or paste text directly', placeholder: 'Please enter product title or description...',
          button: 'Start Translation', loading: 'Translating...', results: 'Translation Results',
          versionA: 'Version A (Standard)', versionB: 'Version B (Cultural Adaptation)',
          tagDirect: 'Concise and Direct', tagLocalized: 'High Localization Level',
          coreSellingPoints: 'Core Selling Points', seoKeywords: 'SEO Keyword Suggestions',
          culturalNotes: 'Cultural Sensitivity Tips', targetLang: 'Target Language',
          category: 'Product Category', country: 'Target Country'
        },
        export: { txt: 'Export TXT', excel: 'Export Excel' },
        languages: {
          zh: 'Chinese', en: 'English', ar: 'Arabic', fr: 'French',
          de: 'German', ja: 'Japanese', ko: 'Korean', ru: 'Russian',
          th: 'Thai', vi: 'Vietnamese', ms: 'Malay', id: 'Indonesian',
          tl: 'Filipino'
        },
        categories: {
          all: 'All Departments', clothes: 'Clothing', '3c': '3C Electronics', home: 'Home Products',
          beauty: 'Beauty & Skincare', baby: 'Baby Products', sports: 'Sports & Outdoors',
          car: 'Car Products'
        },
        dashboard: {
          title: 'Operation Dashboard',
          totalUsers: 'Total Users',
          totalTranslations: 'Total Translations',
          totalProducts: 'Total Products',
          totalRevenue: 'Total Revenue',
          fromLastMonth: 'from last month',
          translationTrend: 'Translation Trend',
          recommendationSystem: 'Recommendation System'
        },
        countries: {
          saudi: 'Saudi Arabia', uae: 'United Arab Emirates', usa: 'United States',
          uk: 'United Kingdom', france: 'France', germany: 'Germany',
          japan: 'Japan', korea: 'Korea', china: 'China',
          russia: 'Russia', thailand: 'Thailand', vietnam: 'Vietnam',
          malaysia: 'Malaysia', indonesia: 'Indonesia',
          philippines: 'Philippines', singapore: 'Singapore'
        },
        common: {
          noData: 'No Data', statusActive: 'Active', statusInactive: 'Inactive',
          cancel: 'Cancel', save: 'Save', refresh: 'Refresh Data', none: 'None',
          previousPage: 'Previous Page', nextPage: 'Next Page'
        },
        system: {
          logManagement: 'Log Management',
          operation: 'Operation',
          user: 'User',
          ip: 'IP',
          time: 'Time',
          details: 'Details'
        },
        merchantApproval: {
          title: 'Merchant Approval',
          pendingApplications: 'Pending Applications',
          shopName: 'Shop Name',
          username: 'Username',
          email: 'Email',
          mainCategory: 'Main Category',
          contactName: 'Contact Name',
          contactPhone: 'Contact Phone',
          actions: 'Actions',
          approve: 'Approve',
          reject: 'Reject',
          applications: 'applications'
        },
        userManagement: {
          title: 'Account Management', addUser: 'Add User',
          table: {
            username: 'Account', role: 'Role', status: 'Status',
            createTime: 'Creation Time', actions: 'Actions'
          },
          actions: { edit: 'Edit', delete: 'Delete' },
          modal: {
            addTitle: 'Add User', editTitle: 'Edit User',
            username: 'Account', password: 'Password (optional for edit)'
          }
        },
        dataAnalysis: {
          title: 'Data Analysis and Effect Report',
          recommendStats: 'Localization Recommendation Statistics',
          translateStats: 'Smart Translation Statistics',
          selectDate: 'Select Date',
          refresh: 'Refresh Data',
          dailyExposure: 'Total Daily Exposure',
          exposureDescription: 'Recommended product display times',
          dailyClicks: 'Total Daily Clicks',
          clicksDescription: 'User click product times',
          clickRate: 'Daily Click Rate',
          clickRateDescription: 'Clicks / Exposure',
          topProducts: 'Product Click Ranking Top10',
          rank: 'Rank',
          productId: 'Product ID',
          clickCount: 'Click Count',
          costSavingSummary: 'Cost Saving Summary (This Month)',
          totalChars: 'Total Translated Characters',
          manualCost: 'Manual Translation Cost (Estimated)',
          savedCost: 'Actual Saved Cost',
          costSavingDescription: 'This month through smart translation, we have processed {total_chars} characters, which is equivalent to saving ¥{saved_cost} in manual translation costs',
          todayTranslateData: 'Today\'s Translation Data',
          charCount: 'Translated Characters',
          apiCost: 'API Call Cost',
          monthlyData: 'Monthly Accumulated Data',
          totalApiCost: 'Total API Cost',
          costSavingRate: 'Cost Saving Rate',
          translateVolumeTrend: 'Translation Volume Trend (Last 30 Days)',
          apiCostTrend: 'API Cost Trend (Last 30 Days)'
        },
        recommend: {
          title: 'Smart Recommendation',
          noResults: 'No recommendation results',
          productId: 'ID',
          origin: 'Origin',
          country: 'Target Country',
          category: 'Product Category',
          searchPlaceholder: 'Search products...',
          searchButton: 'Search',
          loading: 'Loading...',
          recommended: 'Recommended for You',
          hotProducts: 'Hot Products',
          allProducts: 'All Products',
          resultsCount: 'Found',
          items: 'items',
          categories: 'Categories',
          allCategories: 'All',
          priceRange: 'Price Range',
          allPrices: 'All',
          below100: 'Below ¥100',
          between100500: '¥100-500',
          above500: 'Above ¥500',
          noResultsTitle: 'No products found',
          noResultsMessage: 'Please try other keywords',
          backToHome: 'Back to Home',
          previous: 'Previous',
          page: 'Page',
          of: 'of',
          next: 'Next'
        },
        profile: {
          title: 'Personal Center',
          username: 'Username',
          email: 'Email',
          avatar: 'Avatar',
          password: 'Password',
          newPasswordPlaceholder: 'Enter new password',
          save: 'Save Changes',
          edit: 'Edit',
          editProfile: 'Edit Profile',
          cancel: 'Cancel',
          success: 'Saved successfully!',
          shopInfo: 'Shop Information',
          shopName: 'Shop Name',
          mainCategory: 'Main Category',
          contactName: 'Contact Name',
          contactPhone: 'Contact Phone',
          contactEmail: 'Contact Email',
          country: 'Country',
          gender: 'Gender',
          occupation: 'Occupation'
        },
        publish: {
          title: 'Product Publish',
          basicInfo: 'Basic Information',
          titleLabel: 'Product Title',
          titlePlaceholder: 'Enter product title',
          categoryLabel: 'Product Category',
          descriptionLabel: 'Product Description',
          descriptionPlaceholder: 'Enter detailed product description',
          keywordsLabel: 'Keywords',
          keywordsPlaceholder: 'Enter product keywords, separated by commas',
          publishSettings: 'Publish Settings',
          targetCountryLabel: 'Target Country',
          targetLanguagesLabel: 'Target Languages',
          targetPlatformsLabel: 'Target Platforms',
          publishButton: 'Publish Product',
          publishResult: 'Publish Result',
          productId: 'Product ID',
          translationCount: 'Number of Translation Languages',
          complianceCount: 'Number of Compliance Platforms',
          publishAnother: 'Publish Another Product'
        },
        productDetail: {
          title: 'Product Detail',
          loading: 'Loading...',
          home: 'Home',
          searchResults: 'Search Results',
          product: 'Product',
          productId: 'Product ID',
          origin: 'Origin',
          category: 'Category',
          productDescription: 'Product Description',
          noDescription: 'No description available',
          quantity: 'Quantity',
          buyNow: 'Buy Now',
          productDetails: 'Product Details',
          noDetails: 'No detailed information available',
          purchaseSuccess: 'Purchase Successful',
          orderSubmitted: 'Your order has been submitted successfully!',
          confirm: 'Confirm',
          productImage: 'Product image',
          rating: 'Rating',
          defaultCountry: 'China'
        },
        productListing: {
          title: 'Product List'
        },
        searchResults: {
          title: 'Search Results'
        },
        productPublish: {
          title: 'Product Publish'
        },
        merchantProducts: {
          title: 'My Product Management',
          searchPlaceholder: 'Search products',
          searchButton: 'Search',
          editButton: 'Edit',
          unpublishButton: 'Unpublish',
          loading: 'Loading...',
          noProducts: 'No products',
          editModalTitle: 'Edit Product',
          titleChinese: 'Product Title (Chinese)',
          titleForeign: 'Product Title ({language})',
          descChinese: 'Product Description (Chinese)',
          descForeign: 'Product Description ({language})',
          categoryLabel: 'Product Category',
          cancelButton: 'Cancel',
          saveButton: 'Save',
          categories: {
            clothes: 'Clothing',
            '3c': '3C Electronics',
            home: 'Home Goods',
            beauty: 'Beauty',
            baby: 'Baby Products',
            sports: 'Sports',
            car: 'Car Accessories'
          }
        },
        glossary: {
          title: 'Glossary Management',
          search: 'Search Terms',
          addTerm: 'Add Term',
          chineseTerm: 'Chinese Term',
          foreignTerm: 'Foreign Term',
          language: 'Language',
          category: 'Category',
          note: 'Note',
          actions: 'Actions',
          editTitle: 'Edit Term',
          addTitle: 'Add Term',
          save: 'Save',
          cancel: 'Cancel'
        },
        translationHistory: {
          title: 'Translation History',
          totalRecords: 'Total {total} records',
          previousPage: 'Previous Page',
          nextPage: 'Next Page',
          noRecords: 'No translation history records',
          originalText: 'Original Text',
          targetLanguage: 'Target Language',
          translationTime: 'Translation Time',
          versionA: 'Version A (Standard Translation)',
          versionB: 'Version B (Cultural Adaptation)'
        },

      },
      ar: {
        system: { title: "التجارة الإلكترونية العابرة للحدود والترجمة والتوصية", logManagement: "إدارة السجلات", operation: "العملية", user: "المستخدم", ip: "IP", time: "الوقت", details: "التفاصيل" },
        nav: {
          login: 'تسجيل الدخول', register: 'التسجيل', translate: 'الترجمة الذكية',
          translateHistory: 'سجل الترجمة', recommend: 'نظام التوصية',
          profile: 'المركز الشخصي', logout: 'تسجيل الخروج', account: 'إدارة الحساب',
          log: 'إدارة السجلات', glossary: 'المصطلحات',
          productPublish: 'نشر المنتج', merchantProducts: 'إدارة المنتجات',
          productListing: 'قائمة المنتجات',
          'system/user': 'إدارة الحساب',
          'system/merchant': 'مراجعة التجار',
          'system/log': 'إدارة السجلات',
          dashboard: 'لوحة التشغيل'
        },
        login: {
          title: 'تسجيل الدخول', username: 'اسم المستخدم', password: 'كلمة المرور',
          button: 'تسجيل الدخول', toRegister: 'ليس لديك حساب؟ سجل الآن'
        },
        register: {
          title: 'التسجيل', username: 'اسم المستخدم', email: 'البريد الإلكتروني', password: 'كلمة المرور',
          button: 'التسجيل', toLogin: 'لديك حساب بالفعل؟ سجل الدخول',
          registerType: 'نوع التسجيل', customer: 'عميل', merchant: 'تاجر',
          shopName: 'اسم المتجر', contactName: 'اسم جهة الاتصال', contactPhone: 'هاتف جهة الاتصال', contactEmail: 'بريد جهة الاتصال',
          merchantButton: 'التسجيل كتاجر',
          passwordStrength: 'قوة كلمة المرور', weak: 'ضعيف', medium: 'متوسط', strong: 'قوي',
          passwordWeak: '⚠️ كلمة المرور ضعيفة جداً. يرجى تضمين أحرف كبيرة وصغيرة وأرقام ورموز خاصة (8-32 حرف)',
          passwordMedium: '⚡ يُنصح بإضافة رموز خاصة لزيادة قوة كلمة المرور',
          passwordStrong: '✅ قوة كلمة المرور جيدة', passwordLength: 'يرجى إدخال 8-32 حرف'
        },
        translate: {
          title: 'الترجمة الذكية للتجارة الإلكترونية', sourceFile: 'رفع ملف (TXT/DOCX)',
          uploadText: 'انقر أو اسحب الملف هنا', uploadHint: 'يدعم .txt / .docx، التحليل التلقائي وملء النص',
          orText: 'أو الصق النص مباشرة', placeholder: 'الرجاء إدخال عنوان المنتج أو الوصف...',
          button: 'بدء الترجمة', loading: 'جارٍ الترجمة...', results: 'نتائج الترجمة',
          versionA: 'الإصدار أ (قياسي)', versionB: 'الإصدار ب (تكيف ثقافي)',
          tagDirect: 'مباشر وموجز', tagLocalized: 'تكيف ثقافي عالي',
          coreSellingPoints: 'نقاط البيع الأساسية', seoKeywords: 'اقتراحات كلمات SEO',
          culturalNotes: 'ملاحظات الحساسية الثقافية', targetLang: 'اللغة الهدف',
          category: 'فئة المنتج', country: 'البلد المستهدف'
        },
        export: { txt: 'تصدير TXT', excel: 'تصدير Excel' },
        languages: {
          zh: 'الصينية', en: 'الإنجليزية', ar: 'العربية', fr: 'الفرنسية',
          de: 'الألمانية', ja: 'اليابانية', ko: 'الكورية', ru: 'الروسية',
          th: 'التايلاندية', vi: 'الفيتنامية', ms: 'الملايو', id: 'الإندونيسية',
          tl: 'الفلبينية'
        },
        categories: {
          all: 'جميع الأقسام', clothes: 'الملابس', '3c': 'إلكترونيات 3C', home: 'منتجات منزلية',
          beauty: 'الجمال والعناية بالبشرة', baby: 'منتجات الأطفال', sports: 'الرياضة في الهواء الطلق',
          car: 'منتجات السيارات'
        },
        dashboard: {
          title: 'لوحة التشغيل',
          totalUsers: 'إجمالي المستخدمين',
          totalTranslations: 'إجمالي الترجمات',
          totalProducts: 'إجمالي المنتجات',
          totalRevenue: 'إجمالي الإيرادات',
          fromLastMonth: 'من الشهر الماضي',
          translationTrend: 'اتجاه الترجمة',
          recommendationSystem: 'نظام التوصية'
        },
        countries: {
          saudi: 'المملكة العربية السعودية', uae: 'الإمارات العربية المتحدة', usa: 'الولايات المتحدة',
          uk: 'المملكة المتحدة', france: 'فرنسا', germany: 'ألمانيا',
          japan: 'اليابان', korea: 'كوريا', china: 'الصين',
          russia: 'روسيا', thailand: 'تايلاند', vietnam: 'فيتنام',
          malaysia: 'ماليزيا', indonesia: 'إندونيسيا',
          philippines: 'الفلبين', singapore: 'سنغافورة'
        },
        common: {
          noData: 'لا توجد بيانات', statusActive: 'نشط', statusInactive: 'معطل',
          cancel: 'إلغاء', save: 'حفظ', refresh: 'تحديث البيانات', none: 'لا شيء',
          previousPage: 'الصفحة السابقة', nextPage: 'الصفحة التالية'
        },
        system: {
          logManagement: 'إدارة السجلات',
          operation: 'العملية',
          user: 'المستخدم',
          ip: 'IP',
          time: 'الوقت',
          details: 'التفاصيل'
        },
        merchantApproval: {
          title: 'مراجعة التجار',
          pendingApplications: 'طلبات معلقة',
          shopName: 'اسم المتجر',
          username: 'اسم المستخدم',
          email: 'البريد الإلكتروني',
          mainCategory: 'الفئة الرئيسية',
          contactName: 'اسم جهة الاتصال',
          contactPhone: 'هاتف جهة الاتصال',
          actions: 'الإجراءات',
          approve: 'موافقة',
          reject: 'رفض',
          applications: 'طلبات'
        },
        userManagement: {
          title: 'إدارة الحساب', addUser: 'إضافة مستخدم',
          table: {
            username: 'الحساب', role: 'الدور', status: 'الحالة',
            createTime: 'وقت الإنشاء', actions: 'الإجراءات'
          },
          actions: { edit: 'تعديل', delete: 'حذف' },
          modal: {
            addTitle: 'إضافة مستخدم', editTitle: 'تعديل المستخدم',
            username: 'الحساب', password: 'كلمة المرور (اختياري للتعديل)'
          }
        },
        dataAnalysis: {
          title: 'تحليل البيانات والتقارير',
          recommendStats: 'إحصائيات التوصية',
          translateStats: 'إحصائيات الترجمة',
          selectDate: 'اختر التاريخ',
          refresh: 'تحديث',
          dailyExposure: 'إجمالي التعرض اليومي',
          exposureDescription: 'عدد مرات عرض المنتج الموصى به',
          dailyClicks: 'إجمالي النقرات اليومية',
          clicksDescription: 'عدد نقرات المستخدم على المنتج',
          clickRate: 'معدل النقر اليومي',
          clickRateDescription: 'النقرات / التعرض',
          topProducts: 'أفضل 10 منتجات',
          rank: 'الترتيب',
          productId: 'معرف المنتج',
          clickCount: 'عدد النقرات',
          costSavingSummary: 'ملخص توفير التكاليف (هذا الشهر)',
          totalChars: 'إجمالي الأحرف المترجمة',
          manualCost: 'تكلفة الترجمة اليدوية (تقدير)',
          savedCost: 'التوفير الفعلي',
          costSavingDescription: 'هذا الشهر من خلال الترجمة الذكية، عالجنا {total_chars} حرف، وهو ما يعادل توفير ¥{saved_cost} في تكاليف الترجمة اليدوية',
          todayTranslateData: 'بيانات الترجمة اليوم',
          charCount: 'عدد الأحرف',
          apiCost: 'تكلفة API',
          monthlyData: 'البيانات الشهرية المتراكمة',
          totalApiCost: 'إجمالي تكلفة API',
          costSavingRate: 'معدل توفير التكاليف',
          translateVolumeTrend: 'اتجاه حجم الترجمة (آخر 30 يوم)',
          apiCostTrend: 'اتجاه تكلفة API (آخر 30 يوم)'
        },
        recommend: {
          title: 'التوصية الذكية',
          noResults: 'لا توجد نتائج توصية',
          productId: 'المعرف',
          origin: 'المصدر',
          country: 'البلد المستهدف',
          category: 'فئة المنتج',
          searchPlaceholder: 'البحث عن منتجات...',
          searchButton: 'بحث',
          loading: 'جارٍ التحميل...',
          recommended: 'موصى به لك',
          hotProducts: 'المنتجات الساخنة',
          allProducts: 'جميع المنتجات',
          resultsCount: 'تم العثور على',
          items: 'منتج',
          categories: 'فئات المنتجات',
          allCategories: 'الكل',
          priceRange: 'نطاق السعر',
          allPrices: 'الكل',
          below100: 'أقل من 100 يوان',
          between100500: '100-500 يوان',
          above500: 'أكثر من 500 يوان',
          noResultsTitle: 'لم يتم العثور على منتجات',
          noResultsMessage: 'الرجاء تجربة كلمات رئيسية أخرى',
          backToHome: 'العودة للرئيسية',
          previous: 'السابق',
          page: 'صفحة',
          of: 'من',
          next: 'التالي'
        },
        profile: {
          title: 'المركز الشخصي',
          username: 'اسم المستخدم',
          email: 'البريد الإلكتروني',
          avatar: 'الصورة الشخصية',
          password: 'كلمة المرور',
          newPasswordPlaceholder: 'أدخل كلمة المرور الجديدة',
          save: 'حفظ التغييرات',
          edit: 'تعديل',
          editProfile: 'تعديل الملف الشخصي',
          cancel: 'إلغاء',
          success: 'تم الحفظ بنجاح!',
          shopInfo: 'معلومات المتجر',
          shopName: 'اسم المتجر',
          mainCategory: 'الفئة الرئيسية',
          contactName: 'اسم جهة الاتصال',
          contactPhone: 'هاتف جهة الاتصال',
          contactEmail: 'بريد جهة الاتصال',
          country: 'البلد',
          gender: 'الجنس',
          occupation: 'المهنة'
        },
        publish: {
          title: 'نشر المنتج',
          basicInfo: 'المعلومات الأساسية',
          titleLabel: 'عنوان المنتج',
          titlePlaceholder: 'أدخل عنوان المنتج',
          categoryLabel: 'فئة المنتج',
          descriptionLabel: 'وصف المنتج',
          descriptionPlaceholder: 'أدخل وصف المنتج بالتفصيل',
          keywordsLabel: 'الكلمات الرئيسية',
          keywordsPlaceholder: 'أدخل كلمات رئيسية للمنتج، مفصولة بفواصل',
          publishSettings: 'إعدادات النشر',
          targetCountryLabel: 'البلد المستهدف',
          targetLanguagesLabel: 'اللغات المستهدفة',
          targetPlatformsLabel: 'المنصات المستهدفة',
          publishButton: 'نشر المنتج',
          publishResult: 'نتيجة النشر',
          productId: 'معرف المنتج',
          translationCount: 'عدد لغات الترجمة',
          complianceCount: 'عدد منصات فحص الامتثال',
          publishAnother: 'نشر منتج آخر'
        },
        productDetail: {
          title: 'تفاصيل المنتج',
          loading: 'جارٍ التحميل...',
          home: 'الرئيسية',
          searchResults: 'نتائج البحث',
          product: 'المنتج',
          productId: 'معرف المنتج',
          origin: 'المصدر',
          category: 'الفئة',
          productDescription: 'وصف المنتج',
          noDescription: 'لا يوجد وصف',
          quantity: 'الكمية',
          buyNow: 'اشتر الآن',
          productDetails: 'تفاصيل المنتج',
          noDetails: 'لا توجد معلومات تفصيلية',
          purchaseSuccess: 'تم الشراء بنجاح',
          orderSubmitted: 'تم إرسال طلبك بنجاح!',
          confirm: 'تأكيد',
          productImage: 'صورة المنتج',
          rating: 'التقييم',
          defaultCountry: 'الصين'
        },
        productListing: {
          title: 'قائمة المنتجات'
        },
        searchResults: {
          title: 'نتائج البحث'
        },
        productPublish: {
          title: 'نشر المنتج'
        },
        merchantProducts: {
          title: 'إدارة منتجاتي',
          searchPlaceholder: 'البحث عن منتجات',
          searchButton: 'بحث',
          editButton: 'تعديل',
          unpublishButton: 'إلغاء النشر',
          loading: 'جارٍ التحميل...',
          noProducts: 'لا توجد منتجات',
          editModalTitle: 'تعديل المنتج',
          titleChinese: 'عنوان المنتج (بالصينية)',
          titleForeign: 'عنوان المنتج ({language})',
          descChinese: 'وصف المنتج (بالصينية)',
          descForeign: 'وصف المنتج ({language})',
          categoryLabel: 'فئة المنتج',
          cancelButton: 'إلغاء',
          saveButton: 'حفظ',
          categories: {
            clothes: 'الملابس',
            '3c': 'إلكترونيات 3C',
            home: 'منتجات منزلية',
            beauty: 'الجمال',
            baby: 'الأطفال',
            sports: 'الرياضة',
            car: 'إكسسوارات السيارات'
          }
        },
        glossary: {
          title: 'إدارة المصطلحات',
          search: 'البحث عن المصطلحات',
          addTerm: 'إضافة مصطلح',
          chineseTerm: 'المصطلح الصيني',
          foreignTerm: 'المصطلح الأجنبي',
          language: 'اللغة',
          category: 'الفئة',
          note: 'ملاحظة',
          actions: 'الإجراءات',
          editTitle: 'تعديل المصطلح',
          addTitle: 'إضافة مصطلح',
          save: 'حفظ',
          cancel: 'إلغاء'
        },
        translationHistory: {
          title: 'سجل الترجمة',
          totalRecords: 'إجمالي {total} سجل',
          previousPage: 'السابق',
          nextPage: 'التالي',
          noRecords: 'لا توجد سجلات ترجمة',
          originalText: 'النص الأصلي',
          targetLanguage: 'اللغة الهدف',
          translationTime: 'وقت الترجمة',
          versionA: 'الإصدار أ (ترجمة قياسية)',
          versionB: 'الإصدار ب (تكيف ثقافي)'
        },
        aiAssistant: {
          title: 'مساعد الذكاء الاصطناعي',
          searchPlaceholder: 'البحث عن الجلسات...',
          session: 'الجلسة',
          noMessages: 'لا توجد رسائل',
          newSession: 'جلسة جديدة',
          welcomeTitle: 'مرحباً بك في مساعد الذكاء الاصطناعي',
          welcomeSubtitle: 'اكتب الصينية للترجمة التلقائية، الصق اللغة الأجنبية للتحويل التلقائي إلى الصينية',
          me: 'أنا',
          translationResult: 'نتيجة الترجمة',
          aiAssistant: 'مساعد الذكاء الاصطناعي',
          targetLanguage: 'اللغة الهدف',
          serviceLanguage: 'لغة الخدمة',
          inputPlaceholder: 'اكتب رسالتك...',
          clear: 'مسح',
          copyTranslation: 'نسخ نتيجة الترجمة',
          generateReply: 'إنشاء رد بالذكاء الاصطناعي',
          send: 'إرسال'
        },
        logManagement: {
          title: 'إدارة السجلات',
          table: {
            user: 'المستخدم',
            action: 'الإجراء',
            method: 'الطريقة',
            costTime: 'الوقت (مللي ثانية)',
            status: 'الحالة',
            time: 'الوقت'
          },
          statusSuccess: 'نجاح',
          statusFailed: 'فشل'
        }
      }
    }
    
    // 计算属性
    // i18n 兜底：未配置的语言（如 ar 暂无完整词条）回退中文，避免 t.xxx 为 undefined 导致渲染崩溃
    const t = computed(() => messages[lang.value] || messages.zh)

    // 安全取标题：任一语言缺失词条都不应中断渲染
    const safeTitle = (node, fallback = '') => {
      const dict = t.value || {}
      return (dict[node] && dict[node].title) || fallback
    }

    const currentViewTitle = computed(() => {
      // 用 safeTitle 逐项取值：缺失词条回退到本地中文兜底，绝不抛异常
      const titles = {
        login: safeTitle('login', '登录'),
        register: safeTitle('register', '注册'),
        translate: safeTitle('translate', '智能翻译'),
        translateHistory: safeTitle('translationHistory', '翻译历史'),
        recommend: safeTitle('recommend', '本地化推荐'),
        profile: safeTitle('profile', '个人中心'),
        'system/user': safeTitle('userManagement', '账号管理'),
        'system/log': (t.value.system && t.value.system.logManagement) || '日志管理',
        'system/merchant': safeTitle('merchantApproval', '商家审核'),
        publish: safeTitle('publish', '商品发布'),
        dashboard: safeTitle('dashboard', '运营仪表盘'),
        dataAnalysis: safeTitle('dataAnalysis', '数据分析'),
        glossary: safeTitle('glossary', '术语库'),
        productDetail: safeTitle('productDetail', '商品详情'),
        productListing: safeTitle('productListing', '商品列表'),
        searchResults: safeTitle('searchResults', '搜索结果'),
        productPublish: safeTitle('productPublish', '商品发布'),
        merchantProducts: safeTitle('merchantProducts', '商品管理'),
        termFeedback: safeTitle('termFeedback', '术语反哺')
      }
      return titles[currentView.value] || '跨境电商翻译与本地化推荐'
    })

    // ===== 外壳：导航分组、面包屑、语言切换 =====
    // 已登记到 componentMap 的视图（未登记的才走兜底提示，避免出现「组件 + 占位」重复渲染）
    const knownViews = [
      'login', 'register', 'translate', 'translateHistory', 'recommend', 'profile',
      'glossary', 'system/user', 'system/log', 'system/merchant', 'dashboard',
      'dataAnalysis', 'productDetail', 'productListing', 'searchResults',
      'productPublish', 'merchantProducts', 'termFeedback'
    ]

    const VIEW_ICONS = {
      login: 'login', register: 'register',
      translate: 'translate', translateHistory: 'history', glossary: 'glossary', termFeedback: 'feedback',
      productPublish: 'publish', merchantProducts: 'products',
      recommend: 'recommend', dashboard: 'dashboard', dataAnalysis: 'analytics',
      productListing: 'search', searchResults: 'search', productDetail: 'detail',
      'system/user': 'users', 'system/merchant': 'merchant', 'system/log': 'log',
      profile: 'profile'
    }

    // 分组顺序与归属：原型侧栏为「工作台 / 运营 / 买家端 / 系统」四组
    const GROUP_ORDER = ['工作台', '资产', '运营', '买家端', '系统', '个人', '账号']
    const GROUP_OF = {
      translate: '工作台', translateHistory: '工作台',
      productPublish: '工作台', merchantProducts: '工作台',
      glossary: '资产', termFeedback: '资产',
      recommend: '运营', dashboard: '运营', dataAnalysis: '运营',
      productListing: '买家端', searchResults: '买家端', productDetail: '买家端',
      'system/user': '系统', 'system/merchant': '系统', 'system/log': '系统',
      profile: '个人', login: '账号', register: '账号'
    }

    const menuGroups = computed(() => {
      let items
      if (!isLoggedIn.value) {
        items = [
          { path: 'login', label: t.value.nav.login },
          { path: 'register', label: t.value.nav.register }
        ]
      } else {
        items = userMenus.value.map((m) => ({
          path: m.path,
          label: t.value.nav[m.path] || m.menuName
        }))
        // 买家端预览入口：B2 的搜索页此前只能从列表页内跳转，没有导航入口
        if (roleKeys.value.includes('customer') || roleKeys.value.includes('merchant')) {
          items.push({ path: 'searchResults', label: '搜索商品' })
        }
        // 术语反哺：与术语库同属「资产」，后端菜单里没有这一项
        if (roleKeys.value.includes('merchant')) {
          items.push({ path: 'termFeedback', label: '术语反哺' })
        }
      }

      // 去重（profile 可能同时来自后端菜单与默认菜单），保持后端返回的顺序
      const seen = new Set()
      const flat = []
      for (const it of items) {
        if (!it.path || seen.has(it.path)) continue
        seen.add(it.path)
        flat.push(it)
      }

      return GROUP_ORDER.map((title) => ({
        title,
        items: flat
          .filter((it) => (GROUP_OF[it.path] || '个人') === title)
          .map((it) => ({ ...it, icon: VIEW_ICONS[it.path] || '·' }))
      })).filter((g) => g.items.length > 0)
    })

    const breadcrumb = computed(() => {
      const group = GroupOf(currentView.value)
      return group ? `${group} / ${currentViewTitle.value}` : currentViewTitle.value
    })

    function GroupOf(view) {
      return GROUP_OF[view] || ''
    }

    const isBuyerView = computed(() => BUYER_VIEWS.includes(currentView.value))

    // 顶栏不再重复展示界面语言（LangSwitch 已经表达），
    // 目标市场属于发布表单内的状态，壳层拿不到真值时不编造。

    // 侧栏底部不展示实现状态类说明（「未对接平台 API」属开发向信息，已移入开发文档）
    const sidebarFooter = computed(() => '')

    const avatarUrl = computed(() =>
      currentUser.value?.avatar_url ? getAvatarUrl(currentUser.value.avatar_url) : ''
    )

    const avatarInitial = computed(() => {
      const name = currentUser.value?.username || ''
      return name ? name.trim().charAt(0).toUpperCase() : '·'
    })

    // 统一的语言切换：界面语言 + RTL 方向（阿拉伯语）
    const setLang = (code) => {
      if (!['zh', 'en', 'ar'].includes(code)) return
      lang.value = code
      localStorage.setItem('lang', code)
      document.documentElement.lang = code
      document.documentElement.dir = code === 'ar' ? 'rtl' : 'ltr'
    }

    const onNavigate = (path) => {
      sidebarOpen.value = false
      changeRoute(path)
      requestAnimationFrame(() => {
        const el = document.querySelector('.content-wrap')
        if (el) el.scrollTop = 0
      })
    }
    
    // 方法
    const changeRoute = (route) => {
      console.log('changeRoute called with route:', route)
      // 处理带参数的路由，如 productDetail?esin=123
      if (route.includes('?')) {
        const [path, params] = route.split('?')
        console.log('Path:', path, 'Params:', params)
        currentView.value = path
        // 解析参数并存储到状态
        const paramObject = {}
        params.split('&').forEach(param => {
          const [key, value] = param.split('=')
          paramObject[key] = value
        })
        currentViewParams.value = paramObject
        console.log('Current view params:', currentViewParams.value)
      } else {
        currentView.value = route
        currentViewParams.value = {}
      }
      console.log('Current view:', currentView.value)
    }
    
    const switchLanguage = () => {
      const languages = ['zh', 'en', 'ar']
      const currentIndex = languages.indexOf(lang.value)
      const nextIndex = (currentIndex + 1) % languages.length
      lang.value = languages[nextIndex]
      localStorage.setItem('lang', lang.value)
      
      // 设置 HTML dir 属性以支持 RTL 语言（阿拉伯语）
      if (lang.value === 'ar') {
        document.documentElement.dir = 'rtl'
        document.documentElement.lang = 'ar'
      } else {
        document.documentElement.dir = 'ltr'
        document.documentElement.lang = lang.value
      }
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
    
    const goToProfile = () => {
      changeRoute('profile')
    }
    
    const login = async () => {
      console.log('Login function called')
      console.log('Login form data:', loginForm.value)
      loading.value.login = true
      try {
        // 导入新的 API
        const { authApi } = await import('./api/index.js')
        
        // 登录逻辑 - 调用后端API进行验证
        console.log('Sending login request to /api/login')
        const response = await authApi.login({
          username: loginForm.value.username, // 后端会将此作为login_id，支持用户名或邮箱
          password: loginForm.value.password
        })
        
        console.log('Login response:', response)
        console.log('Response data:', response.data)
        
        if (response.data.success) {
          // 登录成功
          console.log('Login successful')
          isLoggedIn.value = true
          
          // 保存用户信息
          currentUser.value = {
            username: loginForm.value.username,
            avatar_url: response.data.avatar_url || null
          }

          // 根据角色设置不同的菜单
          const roles = response.data.roleKeys || []
          console.log('User roles:', roles)

          // 使用前端硬编码的菜单（确保菜单完整）
          if (roles.includes('admin')) {
            userMenus.value = [
              { path: 'system/user', menuName: '账号管理' },
              { path: 'system/merchant', menuName: '商家审核' },
              { path: 'system/log', menuName: '日志管理' },
              { path: 'dashboard', menuName: '运营仪表盘' },
              { path: 'profile', menuName: '个人中心' }
            ]
            changeRoute('system/user')
          } else if (roles.includes('merchant')) {
            userMenus.value = [
              { path: 'translate', menuName: '智能翻译' },
              { path: 'translateHistory', menuName: '翻译历史' },
              { path: 'recommend', menuName: '本地化推荐' },
              { path: 'glossary', menuName: '术语库' },
              { path: 'productPublish', menuName: '商品发布' },
              { path: 'merchantProducts', menuName: '商品管理' },
              { path: 'profile', menuName: '个人中心' }
            ]
            changeRoute('translate')
          } else if (roles.includes('customer')) {
            userMenus.value = [
              { path: 'productListing', menuName: '商品列表' },
              { path: 'profile', menuName: '个人中心' }
            ]
            changeRoute('productListing')
          } else {
            userMenus.value = [
              { path: 'profile', menuName: '个人中心' }
            ]
            changeRoute('profile')
          }
          roleKeys.value = roles

          // 确定用户角色，优先检查商家角色
          let userRole = 'customer'
          if (roles.includes('merchant')) {
            userRole = 'merchant'
          } else if (roles.includes('admin')) {
            userRole = 'admin'
          } else if (roles.includes('customer')) {
            userRole = 'customer'
          }

          // 保存用户信息到本地存储（用于展示）
          const { saveUserInfoToLocalStorage } = await import('./api/index.js')
          saveUserInfoToLocalStorage({
            username: response.data.username,
            user_id: response.data.user_id,
            roleKeys: roles,
            menus: response.data.menus || userMenus.value
          })
          localStorage.setItem('loginRole', userRole)
          localStorage.setItem('loginRoleKeys', JSON.stringify(roles))
          localStorage.setItem('loginEmail', response.data.email || '')

          // 保存用户国家信息到 userProfile（用于商品价格换算）
          const userProfile = {
            country: response.data.country || '中国',
            gender: '',
            occupation: ''
          }
          localStorage.setItem('userProfile', JSON.stringify(userProfile))

          // 保存 JWT Token 到 localStorage
          if (response.data.token) {
            localStorage.setItem('jwt_token', response.data.token)
            console.log('JWT Token saved to localStorage')
          }

          console.log('=== Login info saved ===')
          console.log('Username:', response.data.username)
          console.log('User ID:', response.data.user_id)
          console.log('Role:', userRole)
        } else {
          // 登录失败，显示错误信息
          console.error('Login failed:', response.data.message || response.data.msg)
          alert('登录失败: ' + (response.data.message || response.data.msg))
        }
      } catch (error) {
        console.error('Login failed:', error)
        console.error('Error details:', error.response || error.message)
        alert('登录失败: ' + (error.message || '未知错误'))
      } finally {
        loading.value.login = false
        console.log('Login process completed')
      }
    }
    
    const register = async () => {
      loading.value.reg = true
      try {
        // 导入新的 API
        const { authApi } = await import('./api/index.js')
        // 注册逻辑 - 调用后端API
        const response = await authApi.register({
          username: regForm.value.username,
          email: regForm.value.email,
          password: regForm.value.password
        })
        
        console.log('Register response:', response)
        
        if (response.data.success) {
          // 注册成功，跳转到登录页
          alert('注册成功，请登录')
          changeRoute('login')
        } else {
          // 注册失败，显示错误信息
          console.error('Register failed:', response.data.message || response.data.msg)
          alert('注册失败: ' + (response.data.message || response.data.msg))
        }
      } catch (error) {
        console.error('Register failed:', error)
        alert('注册失败: ' + error.message)
      } finally {
        loading.value.reg = false
      }
    }
    
    const registerMerchant = async () => {
      loading.value.reg = true
      try {
        // 按需导入axios - 商家注册可能不需要登录
        const { default: axios } = await import('axios')
        // 商家注册逻辑 - 调用后端API
        const apiBaseUrl = `${window.location.protocol}//${window.location.host}${import.meta.env.VITE_API_BASE || ''}`
        const response = await axios.post(`${apiBaseUrl}/api/register/merchant`, {
          username: merchantRegForm.value.username,
          email: merchantRegForm.value.email,
          password: merchantRegForm.value.password,
          shop_name: merchantRegForm.value.shop_name,
          main_category: merchantRegForm.value.main_category,
          contact_name: merchantRegForm.value.contact_name,
          contact_phone: merchantRegForm.value.contact_phone,
          contact_email: merchantRegForm.value.contact_email
        })
        
        console.log('Merchant register response:', response)
        
        if (response.data.success) {
          // 注册成功，跳转到登录页
          alert('商家注册成功，等待管理员审核')
          changeRoute('login')
        } else {
          // 注册失败，显示错误信息
          console.error('Merchant register failed:', response.data.message || response.data.msg)
          alert('商家注册失败: ' + (response.data.message || response.data.msg))
        }
      } catch (error) {
        console.error('Merchant register failed:', error)
        alert('商家注册失败: ' + error.message)
      } finally {
        loading.value.reg = false
      }
    }
    
    const translate = async () => {
      loadingTranslate.value = true
      try {
        // 导入新的 API
        const { translateApi } = await import('./api/index.js')
        // 翻译逻辑（用户ID从后端JWT Cookie获取，不需要前端传递）
        const response = await translateApi.translate({
          text: form.value.text,
          target_lang: form.value.target_lang,
          category: form.value.category,
          country: form.value.country
        })
        
        console.log('Translate response:', response)
        
        if (response.data.code === 200) {
          translationResult.value = {
            versionA: response.data.data.versionA,
            versionB: response.data.data.versionB,
            core_selling_points: response.data.data.core_selling_points || [],
            cultural_notes: response.data.data.cultural_notes || '',
            seo_keywords: response.data.data.seo_keywords || []
          }
          showResults.value = true
        } else {
          const msg = response.data.msg || response.data.error || '未知错误'
          console.error('Translation failed:', msg)
          alert('翻译失败: ' + msg)
        }
      } catch (error) {
        console.error('Translation failed:', error)
        alert('翻译失败: ' + error.message)
      } finally {
        loadingTranslate.value = false
      }
    }
    
    // 顶部用户菜单（个人中心 / 退出登录）。
    // 此前 avatar 只能点进个人中心，全站没有退出入口，用户只能关闭标签页。
    const userMenuOpen = ref(false)

    const toggleUserMenu = () => {
      userMenuOpen.value = !userMenuOpen.value
    }

    const openProfileFromMenu = () => {
      userMenuOpen.value = false
      changeRoute('profile')
    }

    const handleLogout = () => {
      userMenuOpen.value = false
      clearLoginState()
    }

    // 点击菜单外部或按 Esc 关闭。用 mousedown 触发，避免与菜单项 click 抢时序。
    const onDocumentPointerDown = (event) => {
      if (!userMenuOpen.value) return
      const wrap = event.target && event.target.closest && event.target.closest('.user-menu-wrap')
      if (!wrap) userMenuOpen.value = false
    }

    const onDocumentKeydown = (event) => {
      if (event.key === 'Escape' && userMenuOpen.value) userMenuOpen.value = false
    }

    const logout = async () => {
      clearLoginState()
    }
    
    
    const handleFileSelected = async (file) => {
      console.log('📌 [App] handleFileSelected 被调用')
      console.log('📌 [App] 收到的文件:', file)
      uploadedFileName.value = file.name
      await processFile(file)
    }
    
    const removeUploadedFile = () => {
      uploadedFileName.value = ''
      form.value.text = ''
    }
    
    const processFile = async (file) => {
      try {
        if (file.name.toLowerCase().endsWith('.txt')) {
          const text = await readTextFile(file)
          form.value.text = text
        } else if (file.name.toLowerCase().endsWith('.docx')) {
          const text = await readDocxFile(file)
          form.value.text = text
        } else {
          alert('不支持的文件格式，请上传 .txt 或 .docx 文件')
          uploadedFileName.value = ''
        }
      } catch (error) {
        console.error('文件解析失败:', error)
        alert('文件解析失败，请重试')
        uploadedFileName.value = ''
      }
    }
    
    const readTextFile = (file) => {
      return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = (e) => {
          resolve(e.target.result)
        }
        reader.onerror = () => {
          reject(new Error('读取文件失败'))
        }
        reader.readAsText(file, 'UTF-8')
      })
    }
    
    const readDocxFile = async (file) => {
      const { default: mammoth } = await import('mammoth')
      const arrayBuffer = await file.arrayBuffer()
      const result = await mammoth.extractRawText({ arrayBuffer })
      return result.value
    }
    
    const handleTextInput = () => {
      isTextInputting.value = form.value.text.trim().length > 0
    }
    
    const selectVersion = (version) => {
      selectedVersion.value = version
    }
    
    const toggleCard = (card) => {
      expandedCards.value[card] = !expandedCards.value[card]
    }
    
    const handleExport = (format) => {
      // 导出逻辑
    }
    
    // 生命周期
    onMounted(async () => {
      // 恢复上次的界面语言与书写方向
      document.documentElement.lang = lang.value
      document.documentElement.dir = lang.value === 'ar' ? 'rtl' : 'ltr'

      // 用户菜单的「点击外部关闭 / Esc 关闭」
      document.addEventListener('mousedown', onDocumentPointerDown)
      document.addEventListener('keydown', onDocumentKeydown)

      // 检查localStorage中是否有登录信息
      const savedUsername = localStorage.getItem('loginUsername')
      const savedUserId = localStorage.getItem('loginUserId')
      const savedRole = localStorage.getItem('loginRole')
      const savedRoleKeysStr = localStorage.getItem('loginRoleKeys')
      const jwtToken = localStorage.getItem('jwt_token')
      
      console.log('=== Checking login state from localStorage ===')
      console.log('Saved username:', savedUsername)
      console.log('Saved userId:', savedUserId)
      console.log('Saved role:', savedRole)
      console.log('Saved roleKeys:', savedRoleKeysStr)
      console.log('Has JWT token:', !!jwtToken)
      
      if (savedUsername && savedUserId && jwtToken) {
        try {
          // 尝试验证 JWT token 有效性（通过获取用户信息）
          console.log('Verifying JWT token...')
          const { profileApi } = await import('./api/index.js')
          const response = await profileApi.getUserInfo()
          
          if (response.data.code === 200) {
            // Token 有效，恢复登录状态
            console.log('JWT token is valid, restoring login state')
            isLoggedIn.value = true
            
            // 从 localStorage 恢复角色信息
            let savedRoleKeys = []
            if (savedRoleKeysStr) {
              try {
                savedRoleKeys = JSON.parse(savedRoleKeysStr)
              } catch (e) {
                savedRoleKeys = []
              }
            }
            roleKeys.value = savedRoleKeys
            console.log('Restored roleKeys:', roleKeys.value)
            
            // 根据角色恢复正确的菜单
            if (savedRoleKeys.includes('admin')) {
              userMenus.value = [
                { path: 'system/user', menuName: '账号管理' },
                { path: 'system/merchant', menuName: '商家审核' },
                { path: 'system/log', menuName: '日志管理' },
                { path: 'dashboard', menuName: '运营仪表盘' },
                { path: 'profile', menuName: '个人中心' }
              ]
              changeRoute('system/user')
            } else if (savedRoleKeys.includes('merchant')) {
              userMenus.value = [
                { path: 'translate', menuName: '智能翻译' },
                { path: 'translateHistory', menuName: '翻译历史' },
                { path: 'recommend', menuName: '本地化推荐' },
                { path: 'glossary', menuName: '术语库' },
                { path: 'productPublish', menuName: '商品发布' },
                { path: 'merchantProducts', menuName: '商品管理' },
                { path: 'profile', menuName: '个人中心' }
              ]
              changeRoute('translate')
            } else if (savedRoleKeys.includes('customer')) {
              userMenus.value = [
                { path: 'productListing', menuName: '商品列表' },
                { path: 'profile', menuName: '个人中心' }
              ]
              changeRoute('productListing')
            } else {
              userMenus.value = [
                { path: 'profile', menuName: '个人中心' }
              ]
              changeRoute('profile')
            }
            
            console.log('Login state restored successfully')
          } else {
            // Token 无效，清除登录状态
            console.log('JWT token is invalid, clearing login state')
            clearLoginState()
          }
        } catch (error) {
          // 请求失败，清除登录状态
          console.error('Error verifying JWT token:', error)
          clearLoginState()
        }
      } else {
        // 没有登录信息，显示登录界面
        console.log('No valid login info found, showing login page')
        clearLoginState()
      }
    })
    
    // 清除登录状态的辅助函数
    const clearLoginState = () => {
      localStorage.removeItem('jwt_token')
      localStorage.removeItem('loginUsername')
      localStorage.removeItem('loginUserId')
      localStorage.removeItem('loginRole')
      localStorage.removeItem('loginRoleKeys')
      localStorage.removeItem('loginEmail')
      localStorage.removeItem('loginMenus')
      isLoggedIn.value = false
      showAssistant.value = false
      userMenus.value = []
      roleKeys.value = []
      changeRoute('login')
    }

    onUnmounted(() => {
      document.removeEventListener('mousedown', onDocumentPointerDown)
      document.removeEventListener('keydown', onDocumentKeydown)
    })

    return {
      currentView,
      currentViewParams,
      currentComponent,
      isLoggedIn,
      showAssistant,
      userMenus,
      roleKeys,
      lang,
      t,
      currentViewTitle,
      userMenuOpen,
      toggleUserMenu,
      openProfileFromMenu,
      handleLogout,
      // 外壳
      sidebarOpen,
      menuGroups,
      breadcrumb,
      sidebarFooter,
      avatarUrl,
      avatarInitial,
      isBuyerView,
      setLang,
      onNavigate,
      knownViews,
      loginForm,
      regForm,
      merchantRegForm,
      form,
      loading,
      loadingTranslate,
      showResults,
      translationResult,
      selectedVersion,
      expandedCards,
      uploadedFileName,
      isTextInputting,
      currentUser,
      changeRoute,
      switchLanguage,
      getAvatarUrl,
      goToProfile,
      login,
      register,
      registerMerchant,
      logout,
      translate,
      handleFileSelected,
      removeUploadedFile,
      handleTextInput,
      selectVersion,
      toggleCard,
      handleExport,
      clearLoginState
    }
  }
}
</script>

<style>
/* 样式已移至 assets/css（tokens → main → utilities） */

/* ===== AI 客服助手浮动入口 ===== */
.assistant-fab {
  position: fixed;
  right: 26px;
  bottom: 26px;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(150deg, #5f7ff0 0%, #3b5bdb 60%, #2f4bc4 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 10px 24px -8px rgba(59, 91, 219, .55), inset 0 1px 0 rgba(255, 255, 255, .22);
  transition: transform .2s var(--ease-out, ease), box-shadow .2s var(--ease-out, ease);
  z-index: 1200;
}
.assistant-fab::after {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 1px solid rgba(59, 91, 219, .25);
  opacity: 0;
  transition: opacity .2s ease, inset .2s ease;
}
.assistant-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 14px 30px -8px rgba(59, 91, 219, .62), inset 0 1px 0 rgba(255, 255, 255, .25);
}
.assistant-fab:hover::after { opacity: 1; inset: -5px; }
.assistant-fab:active { transform: translateY(0) scale(.97); }
.assistant-fab-active {
  background: linear-gradient(150deg, #667085 0%, #475467 100%);
  box-shadow: 0 10px 24px -8px rgba(71, 84, 103, .5);
}

/* 浮层：对齐参考图的聊天窗口比例 */
.assistant-panel {
  position: fixed;
  right: 26px;
  bottom: 92px;
  width: 520px;
  height: 720px;
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 120px);
  background: #fff;
  border-radius: var(--r-lg, 13px);
  border: 1px solid var(--line, #e4e7ee);
  box-shadow: var(--shadow-3, 0 24px 60px -18px rgba(12, 20, 40, .28));
  overflow: hidden;
  z-index: 1199;
  animation: assistant-pop .2s var(--ease-out, ease-out);
}
@keyframes assistant-pop {
  from { opacity: 0; transform: translateY(10px) scale(.99); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}

@media (max-width: 768px) {
  .assistant-fab {
    right: 16px;
    bottom: 16px;
    width: 48px;
    height: 48px;
  }
  .assistant-panel {
    right: 12px;
    left: 12px;
    bottom: 76px;
    width: auto;
    height: min(72vh, 620px);
  }
}
</style>