/**
 * 买家侧 / 发布流程新增文案的三档词表。
 *
 * 为什么不放进 App.vue 的 messages：
 * 那份对象已有 600+ 行，再塞 40 个键要改三处语言分支，容易漏。
 * 这里按「组件用到什么就加什么」维护，缺键时回落中文，不会渲染出 undefined。
 */

const DICT = {
  // 通用
  search: { zh: '搜索', en: 'Search', ar: 'بحث' },
  allCategories: { zh: '全部品类', en: 'All categories', ar: 'كل الفئات' },
  loading: { zh: '加载中…', en: 'Loading…', ar: 'جارٍ التحميل…' },
  prevPage: { zh: '上一页', en: 'Previous', ar: 'السابق' },
  nextPage: { zh: '下一页', en: 'Next', ar: 'التالي' },
  pageOf: { zh: '页', en: 'of', ar: 'من' },
  retry: { zh: '重试', en: 'Retry', ar: 'إعادة' },

  // 搜索页
  totalItems: { zh: '件', en: 'items', ar: 'منتج' },
  totalPrefix: { zh: '共', en: 'Total', ar: 'إجمالي' },
  noKeywordResult: { zh: '关键词无结果', en: 'No match for this keyword', ar: 'لا نتائج لهذه الكلمة' },
  tryAnotherWord: { zh: '换个词', en: 'Try another word', ar: 'جرّب كلمة أخرى' },
  categoryHasStock: { zh: '关键词无结果，品类有货', en: 'No keyword match, but the category has items', ar: 'لا نتائج للكلمة، لكن الفئة تحتوي منتجات' },
  categoryHasStockDesc: {
    zh: '没搜到「{kw}」，但{cat}品类共有 {n} 件商品',
    en: 'Nothing for "{kw}", but {cat} has {n} items',
    ar: 'لا نتائج لـ "{kw}"، لكن فئة {cat} تحتوي {n} منتج'
  },
  viewCategoryAll: { zh: '查看该品类全部', en: 'Browse the category', ar: 'تصفح الفئة' },
  comboTooNarrow: { zh: '组合过窄', en: 'Filters too narrow', ar: 'التصفية ضيقة جدًا' },
  comboTooNarrowDesc: {
    zh: '该品类下没有这个关键词，去掉品类筛选后有 {n} 件',
    en: 'Not in this category. Without the category filter: {n} items',
    ar: 'غير موجود في هذه الفئة. بدون تصفية الفئة: {n} منتج'
  },
  dropCategory: { zh: '去掉品类筛选', en: 'Clear category filter', ar: 'إزالة تصفية الفئة' },
  categoryEmpty: { zh: '分类本身暂无商品', en: 'This category is empty', ar: 'هذه الفئة فارغة' },
  categoryEmptyDesc: {
    zh: '「{cat}」分类下暂无商品',
    en: '"No items in {cat} yet"',
    ar: 'لا توجد منتجات في فئة {cat}'
  },
  viewAllProducts: { zh: '查看全部商品', en: 'Browse all items', ar: 'تصفح كل المنتجات' },
  noProducts: { zh: '暂无商品', en: 'No items', ar: 'لا منتجات' },
  noProductsDesc: {
    zh: '换个关键词，或浏览全部商品',
    en: 'Try another keyword, or browse everything',
    ar: 'جرّب كلمة أخرى أو تصفح الكل'
  },
  fallbackHint: {
    zh: '部分商品暂无该语言译文，已显示其他语言版本并标注',
    en: 'Some items have no translation in this language; another language is shown and labelled',
    ar: 'بعض المنتجات بلا ترجمة بهذه اللغة، وتم عرض لغة أخرى مع توضيحها'
  },

  // 详情页
  language: { zh: '语言', en: 'Language', ar: 'اللغة' },
  noTranslation: { zh: '暂无该语种译文', en: 'No translation in this language', ar: 'لا ترجمة بهذه اللغة' },
  original: { zh: '原文（中文）', en: 'Original (Chinese)', ar: 'الأصل (الصينية)' },
  showOriginal: { zh: '查看原文', en: 'Show original', ar: 'عرض الأصل' },
  hideOriginal: { zh: '收起原文', en: 'Hide original', ar: 'إخفاء الأصل' },
  noRating: { zh: '暂无评价', en: 'No reviews yet', ar: 'لا تقييمات' },
  refPrice: { zh: '参考价', en: 'Reference price', ar: 'سعر مرجعي' },
  esin: { zh: 'ESIN', en: 'ESIN', ar: 'ESIN' },
  esinFull: {
    zh: 'ESIN · E-shop Standard Identification Number（E-shop 标准商品标识）',
    en: 'ESIN · E-shop Standard Identification Number',
    ar: 'ESIN · رقم التعريف القياسي للمنتج في E-shop'
  },
  market: { zh: '目标市场', en: 'Target market', ar: 'السوق المستهدف' },
  langsAvailable: { zh: '已有语种', en: 'Languages', ar: 'اللغات' },
  mainImage: { zh: '商品主图', en: 'Product image', ar: 'صورة المنتج' },
  placeholderImage: { zh: '示意图（暂无实拍图）', en: 'Illustrative image', ar: 'صورة توضيحية' },
  noMoreImages: { zh: '暂无更多图片', en: 'No more images', ar: 'لا مزيد من الصور' },
  addToCart: { zh: '加入购物车（暂未开通）', en: 'Add to cart (unavailable)', ar: 'أضف إلى السلة (غير متاح)' },
  buyNow: { zh: '立即购买（暂未开通）', en: 'Buy now (unavailable)', ar: 'اشتر الآن (غير متاح)' },
  tradeNote: {
    zh: '本店暂未开通在线交易，如需购买请联系商家。',
    en: 'Online checkout is not available yet. Please contact the seller to purchase.',
    ar: 'الشراء الإلكتروني غير متاح حاليًا. يرجى التواصل مع البائع.'
  },
  related: { zh: '同类商品', en: 'Similar items', ar: 'منتجات مشابهة' },
  hotFromRank: { zh: '按点击量排序', en: 'Sorted by clicks', ar: 'مرتبة حسب النقرات' },
  hotInCategory: { zh: '同品类热销', en: 'Popular in this category', ar: 'الأكثر رواجًا في الفئة' },
  noMore: { zh: '没有更多了', en: 'No more items', ar: 'لا مزيد من المنتجات' },
  relatedHint: { zh: '该品类热销商品', en: 'Popular items in this category', ar: 'الأكثر رواجًا في هذه الفئة' },
  relatedEmpty: { zh: '暂无同类商品', en: 'No similar items', ar: 'لا منتجات مشابهة' },
  backToResults: { zh: '返回搜索结果', en: 'Back to results', ar: 'رجوع إلى النتائج' },

  // 发布流程
  stepBasic: { zh: '基本信息', en: 'Basic info', ar: 'المعلومات الأساسية' },
  stepTranslate: { zh: '译文预览', en: 'Translations', ar: 'معاينة الترجمة' },
  stepCompliance: { zh: '合规预检', en: 'Compliance check', ar: 'فحص الامتثال' },
  stepDone: { zh: '完成', en: 'Done', ar: 'تم' },
  prevStep: { zh: '← 上一步', en: '← Back', ar: '→ السابق' },
  nextStep: { zh: '下一步：合规预检 →', en: 'Next: compliance →', ar: 'التالي: الامتثال →' },
  generate: { zh: '生成译文 →', en: 'Generate translations →', ar: 'توليد التراجم →' },
  generating: { zh: '正在生成译文…', en: 'Generating…', ar: 'جارٍ التوليد…' },
  regenerate: { zh: '重新生成该语言', en: 'Regenerate this language', ar: 'أعد توليد هذه اللغة' },
  draft: { zh: '存为草稿', en: 'Save draft', ar: 'حفظ كمسودة' },
  previewSource: { zh: '预览原文', en: 'Preview source', ar: 'معاينة الأصل' },
  standard: { zh: '标准版 · 忠实原文', en: 'Standard · faithful', ar: 'قياسي · مطابق' },
  adapted: { zh: '适配版 · 本地化', en: 'Adapted · localized', ar: 'مكيّف · محلي' },
  adoptAdapted: { zh: '采纳适配版', en: 'Adopt adapted', ar: 'اعتماد المكيّف' },
  keepStandard: { zh: '保留标准版', en: 'Keep standard', ar: 'الإبقاء على القياسي' },
  adoptAll: { zh: '全部用适配版', en: 'Use adapted for all', ar: 'استخدام المكيّف للكل' },
  keepAll: { zh: '全部用标准版', en: 'Use standard for all', ar: 'استخدام القياسي للكل' },
  adoptAllHint: {
    zh: '所有语种一律采用「适配版 · 本地化」，之后仍可逐语种单独调整',
    en: 'Use the adapted version for every language; you can still adjust each one afterwards',
    ar: 'استخدام النسخة المكيّفة لكل اللغات، ويمكن تعديل كل لغة لاحقاً'
  },
  keepAllHint: {
    zh: '所有语种一律采用「标准版 · 忠实原文」，之后仍可逐语种单独调整',
    en: 'Use the standard version for every language; you can still adjust each one afterwards',
    ar: 'استخدام النسخة القياسية لكل اللغات، ويمكن تعديل كل لغة لاحقاً'
  },
  decided: { zh: '已定', en: 'Decided', ar: 'محدد' },
  langsUnit: { zh: '个语种', en: 'languages', ar: 'لغات' },
  charsUnit: { zh: '字', en: 'chars', ar: 'حرف' },
  sameVersion: { zh: '本次两个版本内容一致', en: 'Both versions are identical', ar: 'النسختان متطابقتان' },
  degraded: { zh: '本次译文由备用通道生成，质量可能低于常规结果', en: 'Generated via a backup path; quality may be lower', ar: 'تم التوليد عبر مسار احتياطي وقد تكون الجودة أقل' },
  translationOfDesc: {
    zh: '商品描述译文',
    en: 'Description translation',
    ar: 'ترجمة الوصف'
  },
  glossaryHit: { zh: '术语命中', en: 'Glossary hits', ar: 'مطابقات المصطلحات' },
  culturalNotes: { zh: '文化适配说明', en: 'Localization notes', ar: 'ملاحظات التكييف' },
  culturalNotesHint: { zh: '由翻译模型生成，仅供参考', en: 'Generated by the model · advisory', ar: 'من إنتاج النموذج · للاسترشاد' },
  noCulturalNotes: { zh: '本次未返回适配说明', en: 'No localization notes returned', ar: 'لا ملاحظات' },
  landingRate: { zh: '强制术语落地率', en: 'Mandatory-term landing rate', ar: 'نسبة تطبيق المصطلحات' },
  landingScope: {
    zh: '仅校验主语言的标准版译文',
    en: 'primary language, standard version only',
    ar: 'اللغة الأساسية والنسخة القياسية فقط'
  },
  landingCaveat: {
    zh: '其余语种未做平台预检。',
    en: 'Other languages are not platform-checked.',
    ar: 'بقية اللغات غير مفحوصة.'
  },
  notChecked: { zh: '未校验', en: 'not checked', ar: 'غير مفحوص' },
  violations: { zh: '需处理的违规', en: 'Violations to resolve', ar: 'مخالفات للمعالجة' },
  noViolation: { zh: '没有术语违规', en: 'No glossary violations', ar: 'لا مخالفات مصطلحات' },
  noViolationDesc: {
    zh: '本次译文命中了所有强制术语',
    en: 'All mandatory terms landed in this translation',
    ar: 'تم تطبيق كل المصطلحات الإلزامية'
  },
  platformCheck: { zh: '平台预检', en: 'Platform pre-check', ar: 'فحص المنصات' },
  passed: { zh: '通过', en: 'Passed', ar: 'ناجح' },
  failed: { zh: '未通过', en: 'Failed', ar: 'فاشل' },
  charLimit: { zh: '字符', en: 'chars', ar: 'حرف' },
  noPlatformRule: { zh: '该平台无词表', en: 'no word list for this platform', ar: 'لا قائمة كلمات لهذه المنصة' },
  unresolvedHint: {
    zh: '请在术语库中核对指定译法后修改译文；系统仅提示未落地的术语名称。',
    en: 'Check the required wording in your glossary, then update the translation.',
    ar: 'يرجى التحقق من الصيغة المطلوبة في المصطلحات ثم تعديل الترجمة.'
  },
  doneTitle: { zh: '已生成 {n} 个语种的描述译文草稿', en: '{n} description draft(s) created', ar: 'تم إنشاء مسودة وصف بـ {n} لغة' },
  doneSub: { zh: '商品记录已留档，可随时编辑、下架或重新生成译文', en: 'Saved locally; you can edit, unpublish or regenerate later', ar: 'تم الحفظ محليًا ويمكن التعديل لاحقًا' },
  targetLangs: { zh: '目标语种', en: 'Target languages', ar: 'اللغات المستهدفة' },
  pendingItems: { zh: '待处理违规', en: 'Open violations', ar: 'مخالفات معلقة' },
  publishAnother: { zh: '再发布一个', en: 'Publish another', ar: 'نشر منتج آخر' },
  viewProduct: { zh: '查看商品详情', en: 'View product', ar: 'عرض المنتج' },
  backToCompliance: { zh: '← 返回违规处理', en: '← Back to violations', ar: '→ رجوع إلى المخالفات' },
  productIdLabel: { zh: '商品编号（ESIN）', en: 'Product ID (ESIN)', ar: 'معرف المنتج (ESIN)' },
  failedTitle: { zh: '发布失败', en: 'Publish failed', ar: 'فشل النشر' },
  rerunHint: {
    zh: '重新发布将新增一条商品，原草稿保留',
    en: 'Publishing again adds a new product; the previous draft is kept',
    ar: 'إعادة النشر تضيف منتجًا جديدًا مع الاحتفاظ بالمسودة السابقة'
  }
}

/**
 * 取词函数：缺键回落中文，缺语言回落中文，永不返回 undefined。
 * @param {string} lang zh|en|ar
 * @param {string} key DICT 的键
 * @param {object} vars 占位符变量，形如 { n: 12 }
 */
export function uiText(lang, key, vars) {
  const entry = DICT[key]
  if (!entry) return key
  let text = entry[lang] || entry.zh || key
  if (vars) {
    for (const [k, v] of Object.entries(vars)) {
      text = text.replaceAll(`{${k}}`, String(v))
    }
  }
  return text
}

/** 便捷包装：返回绑定到某个语言的取词函数 */
export function makeT(lang) {
  return (key, vars) => uiText(lang, key, vars)
}

export default DICT
