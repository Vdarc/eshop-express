/**
 * 商品展示的统一取值逻辑。
 *
 * 存在理由：ProductListing / SearchResults / ProductDetail 三个页面原本各写一份
 * getProductImage / getMockRating / formatPrice / getCountryName / getProductTitle，
 * 口径互相不一致（有的读 en_title，有的读 foreign_title；有的伪造评分，有的写死 4.0）。
 * 这里按后端真实返回结构收敛成一份，页面只负责排版。
 *
 * 字段依据（均已回源码核对）：
 * - ProductQuerySupport.formatProduct() 返回：13 语种 title/desc、foreign_title/foreign_desc、
 *   target_language、country、price、display_price、image_url、image_urls
 * - ProductQueryService.formatEsResult() 额外附 score / highlight（ES highlight，键为 `{lang}_title`，值为数组）
 * - ProductQueryService.byEsin() 额外附 thumbnail_urls
 * - CurrencyRates.convert() 返回 display_price = {price, formatted_price, currency, symbol, country}
 */

/** 13 个语种字段（含中文源语言）。这是「存储列数」，不是买家可选语言数。 */
export const ALL_LANGS = ['zh', 'en', 'ar', 'fr', 'de', 'ja', 'ko', 'ru', 'th', 'vi', 'ms', 'id', 'tl']

/** 界面可选语言档位：只有 3 档，与 App.vue 的买家语言一致。 */
export const VIEW_LANGS = [
  { code: 'zh', label: '中文', short: '中文' },
  { code: 'en', label: 'English', short: 'English' },
  { code: 'ar', label: 'العربية', short: 'العربية' }
]

/** 语种本地名称。 */
export const LANG_NATIVE = {
  zh: '中文',
  en: 'English',
  ar: 'العربية',
  fr: 'Français',
  de: 'Deutsch',
  ja: '日本語',
  ko: '한국어',
  ru: 'Русский',
  th: 'ไทย',
  vi: 'Tiếng Việt',
  ms: 'Bahasa Melayu',
  id: 'Bahasa Indonesia',
  tl: 'Tagalog'
}

/** 回退顺序：与后端 TARGET_LANG_PRIORITY 对齐（不含中文），最后兜中文。 */
const FALLBACK_ORDER = ['en', 'ar', 'fr', 'de', 'ja', 'ko', 'ru', 'th', 'vi', 'ms', 'id', 'tl', 'zh']

const isBlank = (v) => v === null || v === undefined || String(v).trim() === ''

/** 判断某语种标题是否为有效值（后端会用 esin 占位填充未生成的语种列）。 */
function usableTitle(product, lang) {
  const v = product?.[`${lang}_title`]
  if (isBlank(v)) return false
  return String(v) !== String(product?.esin ?? '')
}

function usableDesc(product, lang) {
  const v = product?.[`${lang}_desc`]
  if (isBlank(v)) return false
  return String(v) !== String(product?.esin ?? '')
}

/**
 * 解析「该买家实际会看到哪种语言的标题」，并给出是否发生了回退。
 *
 * 背景：后端 formatProduct() 不接收 language 参数，标题语种由卖家发布的目标语言决定
 * （publishTargetLang），取不到时按 FALLBACK_ORDER 探测。所以前端不能假装「买家选什么就显示什么」，
 * 只能优先从 13 个语种字段里取买家所选语言，取不到时如实标注实际语种。
 *
 * @returns {{title:string, desc:string, lang:string, isFallback:boolean, availableLangs:string[]}}
 */
export function resolveDisplay(product, viewerLang = 'zh') {
  if (!product) return { title: '', desc: '', lang: 'zh', isFallback: false, availableLangs: [] }

  const availableLangs = ALL_LANGS.filter((l) => usableTitle(product, l))

  // 1) 买家所选语言有真实译文 → 直接用（这条路不依赖后端的 language 参数）
  if (usableTitle(product, viewerLang)) {
    return {
      title: product[`${viewerLang}_title`],
      desc: usableDesc(product, viewerLang) ? product[`${viewerLang}_desc`] : '',
      lang: viewerLang,
      isFallback: false,
      availableLangs
    }
  }

  // 2) 后端已算出实际语种 → 按它取，与买家所选不同即回退
  const target = product.target_language
  if (target && usableTitle(product, target)) {
    return {
      title: product[`${target}_title`],
      desc: usableDesc(product, target) ? product[`${target}_desc`] : '',
      lang: target,
      isFallback: target !== viewerLang,
      availableLangs
    }
  }

  // 3) 用 foreign_title（后端已做过回退探测）
  //    注意后端兜底 firstNonBlank(en_title, zh_title) 不再判 == esin，
  //    13 列全是 ESIN 占位的商品会把 ESIN 当标题返回，这里再挡一次。
  if (!isBlank(product.foreign_title) && String(product.foreign_title) !== String(product?.esin ?? '')) {
    const detected = target || FALLBACK_ORDER.find((l) => usableTitle(product, l)) || viewerLang
    return {
      title: product.foreign_title,
      desc: isBlank(product.foreign_desc) ? '' : product.foreign_desc,
      lang: detected,
      isFallback: detected !== viewerLang,
      availableLangs
    }
  }

  // 4) 最后兜底：按顺序探测
  const hit = FALLBACK_ORDER.find((l) => usableTitle(product, l))
  if (hit) {
    return {
      title: product[`${hit}_title`],
      desc: usableDesc(product, hit) ? product[`${hit}_desc`] : '',
      lang: hit,
      isFallback: hit !== viewerLang,
      availableLangs
    }
  }

  return { title: '', desc: '', lang: viewerLang, isFallback: false, availableLangs }
}

/** 该商品是否真的有某个语种的译文（用于详情页语言档位置灰）。 */
export function hasLang(product, lang) {
  return usableTitle(product, lang)
}

/**
 * 取后端返回的高亮片段并转成可安全渲染的 HTML。
 *
 * ES 返回的 highlight 形如 { ar_title: ['فستان <em>قطني</em> طويل'] }，键是语种字段名、值是数组。
 * 只放行 <em> 标签：先整体转义，再把转义后的 &lt;em&gt; 还原，
 * 避免商品文案里的尖括号被当成标签执行（XSS）。
 */
export function pickHighlight(product, lang = 'zh') {
  const hl = product?.highlight
  if (!hl || typeof hl !== 'object') return ''

  const keys = [`${lang}_title`, ...FALLBACK_ORDER.map((l) => `${l}_title`)]
  for (const key of keys) {
    const raw = hl[key]
    if (isBlank(raw)) continue
    const text = Array.isArray(raw) ? raw.join(' ') : String(raw)
    if (text.trim()) return sanitizeHighlight(text)
  }
  return ''
}

/** 转义后仅还原 <em>，其余标签一律按文本处理。 */
export function sanitizeHighlight(input) {
  if (!input) return ''
  return String(input)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/&lt;em&gt;/gi, '<em>')
    .replace(/&lt;\/em&gt;/gi, '</em>')
}

/**
 * 是否占位图。
 *
 * 后端在无图时会生成 https://picsum.photos/seed/xxx/300/300 作为占位（见 ProductQuerySupport），
 * 也就是 image_url 永远「有值」；而 ES 路径传 placeholderWhenEmpty=false，无图时又是 null。
 * 两条路径行为不同，所以不能只判空。识别出占位图后改用本地占位块，并显式标注「示意图」，
 * 避免断网时整块空白、也避免买家以为这是真实商品图。
 */
export function isPlaceholderImage(url) {
  if (isBlank(url)) return true
  return /picsum\.photos/i.test(String(url))
}

/** 主图地址：占位图返回 null，交给模板渲染本地占位块。 */
export function mainImage(product) {
  const url = product?.image_url || product?.image_urls?.[0]
  return isPlaceholderImage(url) ? null : url
}

/** 缩略图列表。后端实际只会有 1 张（product_image 全库仅 1 条），不要按 4 张硬造。 */
export function thumbImages(product) {
  const list = product?.thumbnail_urls?.length ? product.thumbnail_urls : product?.image_urls || []
  return list.filter((u) => !isPlaceholderImage(u)).slice(0, 4)
}

/**
 * 价格展示。
 *
 * display_price 有两种可能的形状，这里都兼容：
 * - 对象 `{price, formatted_price, currency, symbol, country}` —— 当前源码实际返回
 *   （`ProductQuerySupport` 把 `CurrencyRates.convert()` 的 Map 直接 put 进去）
 * - 数值 —— 07-API接口文档 §3.2.1 与 FR-B11 写的是这种（文档与源码不符，以源码为准）
 * 只认一种形状的实现，在另一种下会渲染出 `[object Object]` 或丢失币种。
 */
export function formatPrice(product) {
  const dp = product?.display_price
  if (dp && typeof dp === 'object' && !isBlank(dp.formatted_price)) {
    return `${dp.symbol ?? ''}${dp.formatted_price}`
  }
  // 数值形态：没有币种符号可用（文档自己也承认这种情况无法标注币种），只给数字
  if (typeof dp === 'number' && Number.isFinite(dp)) {
    return dp.toFixed(2)
  }
  const price = Number(product?.price ?? 0)
  return `¥${Number.isFinite(price) ? price.toFixed(2) : '0.00'}`
}

/**
 * 价格副标题：说明换算口径，避免买家把外币价当成卖家报价。
 *
 * display_price 只有 {price, formatted_price, currency, symbol, country}，不含 rate，
 * 所以汇率用 display_price.price ÷ product.price 反算 —— 这是唯一与后端同源的算法，
 * 不在前端另存一张汇率表（否则汇率一改就错）。
 */
export function priceMeta(product) {
  const dp = product?.display_price
  if (!dp || isBlank(dp.currency)) return ''
  const base = Number(product?.price ?? 0)
  const converted = Number(dp.price ?? 0)
  if (base > 0 && converted > 0 && dp.currency !== 'CNY') {
    const rate = converted / base
    const shown = rate >= 100 ? rate.toFixed(0) : rate.toFixed(2)
    return `参考价 · 1 CNY = ${shown} ${dp.currency}`
  }
  return `${dp.currency} · 参考价`
}

/** 品类取值归一：库里是 `3C`（大写），下拉/接口里常见 `3c`，比较时统一小写。 */
export function normalizeCategory(value) {
  return String(value ?? '').trim().toLowerCase()
}

/**
 * 品类请求值：返回库中的规范写法。
 *
 * 为什么要这一步：ES 的 category 是 keyword 且无 normalizer（term 查询大小写敏感），
 * 前端传小写 `3c` 在 ES 路径下必然 0 条。库中真实值是 `3C`，所以发请求前归一成规范值，
 * 不动索引也能让两条检索路径一致。
 */
export function categoryRequestValue(value) {
  const key = normalizeCategory(value)
  if (!key || key === 'all') return null
  if (key === '3c') return '3C'
  return key
}

/** 品类中文名。dict 传 i18n 的 categories 节点，缺失时回落到本地映射。 */
export function categoryLabel(category, dict = {}) {
  if (isBlank(category)) return ''
  const key = normalizeCategory(category)
  const FALLBACK = {
    clothes: '服装',
    '3c': '3C 数码',
    home: '家居',
    beauty: '美妆',
    baby: '母婴',
    sports: '运动户外',
    car: '汽车用品',
    all: '全部品类'
  }
  return dict[key] || FALLBACK[key] || String(category)
}

/** 买家侧可筛品类（与库中已有取值的规范写法对应）。 */
export const CATEGORY_OPTIONS = ['clothes', '3c', 'home', 'beauty', 'baby', 'sports', 'car']

/** 国家名。原型的「沙特 · ar」标签用的是这个。 */
export function countryLabel(country, dict = {}) {
  if (isBlank(country)) return ''
  const map = {
    沙特: 'saudi',
    沙特阿拉伯: 'saudi',
    saudi: 'saudi',
    阿联酋: 'uae',
    uae: 'uae',
    美国: 'usa',
    usa: 'usa',
    英国: 'uk',
    uk: 'uk',
    法国: 'france',
    france: 'france',
    德国: 'germany',
    germany: 'germany',
    日本: 'japan',
    japan: 'japan',
    韩国: 'korea',
    korea: 'korea',
    中国: 'china',
    china: 'china',
    俄罗斯: 'russia',
    russia: 'russia',
    泰国: 'thailand',
    thailand: 'thailand',
    越南: 'vietnam',
    vietnam: 'vietnam',
    马来西亚: 'malaysia',
    malaysia: 'malaysia',
    印度尼西亚: 'indonesia',
    印尼: 'indonesia',
    indonesia: 'indonesia',
    菲律宾: 'philippines',
    philippines: 'philippines',
    新加坡: 'singapore',
    singapore: 'singapore'
  }
  const key = map[String(country).trim()] || map[String(country).trim().toLowerCase()]
  return dict[key] || String(country)
}

/** 卡片左上角标签：「沙特 · ar」。 */
export function marketBadge(product, lang) {
  const c = countryLabel(product?.country)
  const l = String(lang || '').toLowerCase()
  return [c, l].filter(Boolean).join(' · ')
}

/**
 * 标准版与适配版是否真的不同。
 *
 * 不做字符级 diff：PRD FR-01 明确否决了这个做法（泰语无词间空格、阿语 RTL 与连写，
 * 前端分词不可靠，会给出一堆假差异）。后端也没有 diff_segments 接口。
 * 所以只做整段并排 + 整段采纳，差异只回答「有没有」，不回答「第几处」。
 */
export function hasDifference(a, b) {
  const left = String(a ?? '').trim()
  const right = String(b ?? '').trim()
  if (!left && !right) return false
  return left !== right
}

/** 文本方向：阿拉伯语从右到左。只加在文本块上，不加在容器上（否则整页布局翻转）。 */
export function textDir(lang) {
  return String(lang || '').toLowerCase() === 'ar' ? 'rtl' : 'ltr'
}

export function escapeHtml(input) {
  return String(input ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}
