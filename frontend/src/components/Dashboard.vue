<template>
  <div>
    <!-- 页头：标题 + 一句话说明 + 日期筛选 -->
    <div class="page-h">
      <div>
        <div class="tt">{{ t.dashboard.title }}</div>
        <div class="sub">翻译量、商品曝光与点击表现的实时概览</div>
      </div>
      <div class="acts">
        <label class="date-field">
          <span>选择日期</span>
          <input v-model="selectedDate" type="date" @change="loadDailyStat" />
        </label>
      </div>
    </div>

    <!-- 加载态：骨架屏，避免整块空白 + 居中转圈 -->
    <div v-if="loading" class="ui-card">
      <div class="ui-card-b">
        <div class="stat-grid">
          <div v-for="n in 3" :key="'sk'+n" class="stat">
            <div class="skel skel-line w-40"></div>
            <div class="skel skel-line" style="height: 26px; width: 55%"></div>
            <div class="skel skel-line w-60" style="margin-top: 10px"></div>
          </div>
        </div>
        <div class="dash-grid" style="margin-top: 16px">
          <div class="skel" style="height: 300px"></div>
          <div class="skel" style="height: 300px"></div>
        </div>
      </div>
    </div>

    <template v-else>
      <!-- 核心指标 -->
      <div class="stat-grid">
        <div class="stat">
          <div class="k">{{ t.dashboard.totalUsers }}</div>
          <div class="v">{{ dashboardData.total_users }}</div>
          <div class="d">
            <span :class="dashboardData.user_growth_rate.startsWith('+') ? 'up' : 'down'">
              {{ dashboardData.user_growth_rate }}
            </span>
            {{ t.dashboard.fromLastMonth }}
          </div>
          <div class="ico" aria-hidden="true">☺</div>
        </div>
        <div class="stat tone-ok">
          <div class="k">{{ t.dashboard.totalTranslations }}</div>
          <div class="v">{{ dashboardData.total_translations.toLocaleString() }}</div>
          <div class="d">
            <span :class="dashboardData.translation_growth_rate.startsWith('+') ? 'up' : 'down'">
              {{ dashboardData.translation_growth_rate }}
            </span>
            {{ t.dashboard.fromLastMonth }}
          </div>
          <div class="ico" aria-hidden="true">⇄</div>
        </div>
        <div class="stat tone-warn">
          <div class="k">{{ t.dashboard.totalProducts }}</div>
          <div class="v">{{ dashboardData.total_products }}</div>
          <div class="d">
            <span :class="dashboardData.product_growth_rate.startsWith('+') ? 'up' : 'down'">
              {{ dashboardData.product_growth_rate }}
            </span>
            {{ t.dashboard.fromLastMonth }}
          </div>
          <div class="ico" aria-hidden="true">▦</div>
        </div>
      </div>

      <!-- 趋势图 -->
      <div class="dash-grid">
        <section class="ui-card">
          <header class="ui-card-h">
            <h3>{{ t.dashboard.translationTrend }}</h3>
            <span class="hint">近 6 个月</span>
          </header>
          <div class="ui-card-b">
            <div ref="translateChartRef" class="chart-box"></div>
          </div>
        </section>
        <section class="ui-card">
          <header class="ui-card-h">
            <h3>{{ t.dashboard.productExposure || '商品曝光' }}</h3>
            <span class="hint">曝光 / 点击</span>
          </header>
          <div class="ui-card-b">
            <div ref="exposureChartRef" class="chart-box"></div>
          </div>
        </section>
      </div>

      <!-- 当日统计 -->
      <div class="sec-head">
        <div class="ui-sec-title">当日统计</div>
        <span class="ui-sub">按所选日期统计，切换日期即时刷新</span>
      </div>
      <div class="stat-grid">
        <div class="stat tone-info">
          <div class="k">当日总曝光数</div>
          <div class="v">{{ dailyStat.total_expose }}</div>
          <div class="d">推荐位商品展示次数</div>
        </div>
        <div class="stat tone-warn">
          <div class="k">当日总点击数</div>
          <div class="v">{{ dailyStat.total_click }}</div>
          <div class="d">用户点击商品次数</div>
        </div>
        <div class="stat tone-ok">
          <div class="k">点击率</div>
          <div class="v">{{ dailyStat.click_rate }}</div>
          <div class="d">点击数 / 曝光数</div>
        </div>
      </div>

      <!-- 商品点击排行 -->
      <section class="ui-card" style="margin-top: 16px">
        <header class="ui-card-h">
          <h3>商品点击排行 Top 10</h3>
          <span class="hint">总体数据</span>
        </header>
        <div class="ui-card-b tight">
          <div class="overflow-x-auto">
            <table>
              <thead>
                <tr>
                  <th style="width: 72px">排名</th>
                  <th>商品ID</th>
                  <th style="width: 140px">点击次数</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in clickRank.top_products" :key="item.product_id">
                  <td>
                    <span class="rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
                  </td>
                  <td class="mono">{{ item.product_id }}</td>
                  <td class="num">{{ item.click_count }}</td>
                </tr>
                <tr v-if="clickRank.top_products.length === 0">
                  <td colspan="3">
                    <div class="ui-empty" style="padding: 28px 12px">
                      <div class="ico">▦</div>
                      <b>暂无数据</b>
                      <p>所选日期内还没有点击记录</p>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script>
import { ref, reactive, onMounted, onUnmounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'

export default {
  name: 'Dashboard',
  props: {
    t: Object
  },
  setup() {
    const loading = ref(true)
    const translateChartRef = ref(null)
    const exposureChartRef = ref(null)
    let translateChart = null
    let exposureChart = null
    let resizeObserver = null
    
    const dashboardData = reactive({
      total_users: 0,
      user_growth_rate: '+12%',
      total_translations: 0,
      translation_growth_rate: '+8%',
      total_products: 0,
      product_growth_rate: '+5%',
      translate_trend: {
        months: ['1月', '2月', '3月', '4月', '5月', '6月'],
        char_counts: [60, 80, 40, 90, 70, 85]
      },
      recommendation_trend: {
        months: ['1月', '2月', '3月', '4月', '5月', '6月'],
        expose_counts: [75, 65, 85, 95, 70, 80],
        click_counts: []
      }
    })
    
    // 当日统计数据
    const selectedDate = ref(new Date().toISOString().split('T')[0])
    const dailyStat = reactive({
      date: '',
      total_expose: 0,
      total_click: 0,
      click_rate: '0%'
    })
    
    // 总体商品点击排行（不随日期变化）
    const clickRank = reactive({
      top_products: []
    })

    const initTranslateChart = () => {
      if (!translateChartRef.value) {
        console.log('翻译图表DOM不存在，重试...')
        setTimeout(initTranslateChart, 100)
        return
      }
      
      const dom = translateChartRef.value
      console.log('翻译图表DOM尺寸:', dom.clientWidth, dom.clientHeight)
      
      if (dom.clientWidth === 0 || dom.clientHeight === 0) {
        console.log('翻译图表尺寸为0，重试...')
        setTimeout(initTranslateChart, 100)
        return
      }
      
      // 如果已存在实例，先销毁
      if (translateChart) {
        translateChart.dispose()
      }
      
      translateChart = echarts.init(dom)
      
      const option = {
        // 与设计令牌同一套配色：品牌靛蓝 / 成功绿 / 警示橙
        color: ['#3b5bdb'],
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: 'rgba(22, 24, 31, .92)',
          borderWidth: 0,
          textStyle: { color: '#fff', fontSize: 12 },
          padding: [8, 12]
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          top: 18,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: dashboardData.translate_trend.months,
          axisLabel: {
            interval: 0,
            color: '#8b92a0',
            fontSize: 11.5
          },
          axisLine: { lineStyle: { color: '#e4e7ee' } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          name: '字符数',
          nameTextStyle: { color: '#aeb4c0', fontSize: 11, padding: [0, 0, 6, 0] },
          axisLabel: { color: '#8b92a0', fontSize: 11.5 },
          splitLine: { lineStyle: { color: '#eef0f5', type: 'dashed' } }
        },
        series: [
          {
            name: '翻译字符数',
            type: 'bar',
            data: dashboardData.translate_trend.char_counts,
            barMaxWidth: 34,
            itemStyle: {
              color: '#3b5bdb',
              borderRadius: [4, 4, 0, 0]
            },
            emphasis: {
              itemStyle: { color: '#2f4bc4' }
            }
          }
        ]
      }
      
      translateChart.setOption(option)
      console.log('翻译图表初始化完成')
    }

    const initExposureChart = () => {
      if (!exposureChartRef.value) {
        console.log('曝光图表DOM不存在，重试...')
        setTimeout(initExposureChart, 100)
        return
      }
      
      const dom = exposureChartRef.value
      console.log('曝光图表DOM尺寸:', dom.clientWidth, dom.clientHeight)
      
      if (dom.clientWidth === 0 || dom.clientHeight === 0) {
        console.log('曝光图表尺寸为0，重试...')
        setTimeout(initExposureChart, 100)
        return
      }
      
      // 如果已存在实例，先销毁
      if (exposureChart) {
        exposureChart.dispose()
      }
      
      exposureChart = echarts.init(dom)
      
      const option = {
        color: ['#0ca678', '#e8590c'],
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(22, 24, 31, .92)',
          borderWidth: 0,
          textStyle: { color: '#fff', fontSize: 12 },
          padding: [8, 12]
        },
        legend: {
          data: ['曝光量', '点击量'],
          top: 0,
          itemWidth: 10,
          itemHeight: 10,
          icon: 'roundRect',
          textStyle: { color: '#4f5665', fontSize: 11.5 }
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '3%',
          top: 34,
          containLabel: true
        },
        xAxis: {
          type: 'category',
          data: dashboardData.recommendation_trend.months,
          axisLabel: {
            interval: 0,
            color: '#8b92a0',
            fontSize: 11.5
          },
          axisLine: { lineStyle: { color: '#e4e7ee' } },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          name: '次数',
          nameTextStyle: { color: '#aeb4c0', fontSize: 11, padding: [0, 0, 6, 0] },
          axisLabel: { color: '#8b92a0', fontSize: 11.5 },
          splitLine: { lineStyle: { color: '#eef0f5', type: 'dashed' } }
        },
        series: [
          {
            name: '曝光量',
            type: 'bar',
            data: dashboardData.recommendation_trend.expose_counts,
            barMaxWidth: 18,
            itemStyle: {
              color: '#0ca678',
              borderRadius: [3, 3, 0, 0]
            },
            emphasis: {
              itemStyle: { color: '#087f5b' }
            }
          },
          {
            name: '点击量',
            type: 'bar',
            data: dashboardData.recommendation_trend.click_counts.length > 0 
              ? dashboardData.recommendation_trend.click_counts 
              : dashboardData.recommendation_trend.expose_counts.map(c => Math.round(c * 0.1)),
            barMaxWidth: 18,
            itemStyle: {
              color: '#e8590c',
              borderRadius: [3, 3, 0, 0]
            },
            emphasis: {
              itemStyle: { color: '#b8460a' }
            }
          }
        ]
      }
      
      exposureChart.setOption(option)
      console.log('曝光图表初始化完成')
    }

    const updateCharts = () => {
      console.log('更新图表数据')
      if (translateChart) {
        translateChart.setOption({
          xAxis: {
            data: dashboardData.translate_trend.months
          },
          series: [
            {
              data: dashboardData.translate_trend.char_counts
            }
          ]
        })
      }
      
      if (exposureChart) {
        exposureChart.setOption({
          xAxis: {
            data: dashboardData.recommendation_trend.months
          },
          series: [
            {
              data: dashboardData.recommendation_trend.expose_counts
            },
            {
              data: dashboardData.recommendation_trend.click_counts.length > 0 
                ? dashboardData.recommendation_trend.click_counts 
                : dashboardData.recommendation_trend.expose_counts.map(c => Math.round(c * 0.1))
            }
          ]
        })
      }
    }

    const handleResize = () => {
      translateChart?.resize()
      exposureChart?.resize()
    }

    const loadDashboardData = async () => {
      try {
        console.log('开始加载仪表盘数据...')
        const { dashboardApi } = await import('../api/index.js')
        const response = await dashboardApi.getData()
        
        console.log('API 响应:', response)
        console.log('响应数据:', response.data)
        
        if (response.data.code === 200) {
          const data = response.data.data
          console.log('后端返回的数据:', data)
          
          dashboardData.total_users = data.total_users || 0
          dashboardData.user_growth_rate = data.user_growth_rate || '+12%'
          dashboardData.total_translations = data.total_translations || 0
          dashboardData.translation_growth_rate = data.translation_growth_rate || '+8%'
          dashboardData.total_products = data.total_products || 0
          dashboardData.product_growth_rate = data.product_growth_rate || '+5%'
          
          if (data.translate_trend) {
            dashboardData.translate_trend.months = data.translate_trend.months || ['1月', '2月', '3月', '4月', '5月', '6月']
            dashboardData.translate_trend.char_counts = data.translate_trend.char_counts || [60, 80, 40, 90, 70, 85]
          }
          
          if (data.recommendation_trend) {
            dashboardData.recommendation_trend.months = data.recommendation_trend.months || ['1月', '2月', '3月', '4月', '5月', '6月']
            dashboardData.recommendation_trend.expose_counts = data.recommendation_trend.expose_counts || [75, 65, 85, 95, 70, 80]
            dashboardData.recommendation_trend.click_counts = data.recommendation_trend.click_counts || []
          }
          
          console.log('更新后的 dashboardData:', dashboardData)
          updateCharts()
        }
      } catch (error) {
        console.error('加载仪表盘数据失败:', error)
      } finally {
        loading.value = false
      }
    }

    // 加载每日统计数据
    const loadDailyStat = async () => {
      try {
        console.log('加载每日统计数据，日期:', selectedDate.value)
        const { statApi } = await import('../api/index.js')
        const response = await statApi.getDaily({ date: selectedDate.value })
        
        console.log('每日统计API响应:', response)
        
        if (response.data.code === 200) {
          const data = response.data.data
          dailyStat.date = data.date || ''
          dailyStat.total_expose = data.total_expose || 0
          dailyStat.total_click = data.total_click || 0
          dailyStat.click_rate = data.click_rate || '0%'
          
          console.log('更新后的每日统计数据:', dailyStat)
        }
      } catch (error) {
        console.error('加载每日统计数据失败:', error)
      }
    }
    
    // 加载总体商品点击排行（不随日期变化）
    const loadClickRank = async () => {
      try {
        console.log('加载总体商品点击排行')
        const { statApi } = await import('../api/index.js')
        const response = await statApi.getClickRank()
        
        console.log('总体点击排行API响应:', response)
        
        if (response.data.code === 200) {
          const data = response.data.data
          clickRank.top_products = data.top_products || []
          
          console.log('更新后的总体点击排行:', clickRank)
        }
      } catch (error) {
        console.error('加载总体点击排行失败:', error)
      }
    }

    watch(loading, async (newVal) => {
      if (!newVal) {
        console.log('Loading完成，准备初始化图表')
        await nextTick()
        await nextTick() // 双保险
        console.log('DOM已更新，等待下一帧')
        
        requestAnimationFrame(() => {
          console.log('requestAnimationFrame触发')
          initTranslateChart()
          initExposureChart()
        })
      }
    })

    onMounted(() => {
      console.log('Dashboard组件已挂载')
      loadDashboardData()
      loadDailyStat()
      loadClickRank()
      window.addEventListener('resize', handleResize)
      
      // 使用 ResizeObserver 监听容器尺寸变化
      resizeObserver = new ResizeObserver(entries => {
        console.log('ResizeObserver 检测到尺寸变化')
        handleResize()
      })
      
      if (translateChartRef.value) {
        resizeObserver.observe(translateChartRef.value)
      }
      if (exposureChartRef.value) {
        resizeObserver.observe(exposureChartRef.value)
      }
    })

    onUnmounted(() => {
      translateChart?.dispose()
      exposureChart?.dispose()
      window.removeEventListener('resize', handleResize)
      if (resizeObserver) {
        resizeObserver.disconnect()
      }
    })

    return {
      loading,
      dashboardData,
      translateChartRef,
      exposureChartRef,
      selectedDate,
      dailyStat,
      clickRank,
      loadDailyStat
    }
  }
}
</script>

<style scoped>
/* 图表双栏：≥1024px 并排，窄屏堆叠（原来靠不存在的 lg:grid-cols-2） */
.dash-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gap);
  margin-top: 16px;
}

.chart-box {
  width: 100%;
  height: 300px;
  min-height: 300px;
}

/* 日期筛选：label 与输入框同一行，视觉上像一个控件组 */
.date-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 4px 0 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: #fff;
  font-size: 12px;
  color: var(--ink-3);
  transition: border-color var(--t-fast) var(--ease-out), box-shadow var(--t-fast) var(--ease-out);
}

.date-field:focus-within {
  border-color: var(--brand);
  box-shadow: 0 0 0 3px var(--brand-ring);
}

.date-field input {
  width: 138px;
  height: 30px;
  min-height: 0;
  border: 0;
  padding: 0 6px;
  font-size: 12.5px;
  background: transparent;
  box-shadow: none;
}

/* 分区标题行 */
.sec-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 22px 0 12px;
  flex-wrap: wrap;
}

.sec-head .ui-sec-title {
  margin-bottom: 0;
}

/* 排名徽标：前三名给一点分量，其余保持安静 */
.rank {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-3);
  background: var(--bg-sink);
}

.rank.top {
  color: #fff;
  background: linear-gradient(150deg, #5f7ff0, #3b5bdb);
}

@media (max-width: 1024px) {
  .dash-grid {
    grid-template-columns: 1fr;
  }
  .chart-box {
    height: 260px;
    min-height: 260px;
  }
}
</style>
