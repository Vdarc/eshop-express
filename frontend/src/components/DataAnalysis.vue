<template>
  <div>
    <div class="page-h">
      <div>
        <div class="tt">{{ t.dataAnalysis.title }}</div>
        <div class="sub">曝光/点击漏斗、翻译成本与趋势的统一视图</div>
      </div>
      <div class="acts">
        <label class="date-field">
          <span>{{ t.dataAnalysis.selectDate }}</span>
          <input type="date" />
        </label>
        <button class="btn btn-primary">{{ t.dataAnalysis.refresh }}</button>
      </div>
    </div>

    <!-- 核心指标 -->
    <div class="stat-grid">
      <div class="stat">
        <div class="k">{{ t.dataAnalysis.dailyExposure }}</div>
        <div class="v">12,547</div>
        <div class="d">{{ t.dataAnalysis.exposureDescription }}</div>
        <div class="ico" aria-hidden="true">◉</div>
      </div>
      <div class="stat tone-info">
        <div class="k">{{ t.dataAnalysis.dailyClicks }}</div>
        <div class="v">3,245</div>
        <div class="d">{{ t.dataAnalysis.clicksDescription }}</div>
        <div class="ico" aria-hidden="true">↗</div>
      </div>
      <div class="stat tone-ok">
        <div class="k">{{ t.dataAnalysis.clickRate }}</div>
        <div class="v">25.8%</div>
        <div class="d">{{ t.dataAnalysis.clickRateDescription }}</div>
        <div class="ico" aria-hidden="true">％</div>
      </div>
    </div>

    <!-- 点击排行 -->
    <section class="ui-card" style="margin-top: 16px">
      <header class="ui-card-h">
        <h3>{{ t.dataAnalysis.topProducts }}</h3>
        <span class="hint">Top 10</span>
      </header>
      <div class="ui-card-b tight">
        <div class="overflow-x-auto">
          <table>
            <thead>
              <tr>
                <th style="width: 80px">{{ t.dataAnalysis.rank }}</th>
                <th>{{ t.dataAnalysis.productId }}</th>
                <th style="width: 160px">{{ t.dataAnalysis.clickCount }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(product, index) in topProducts" :key="index">
                <td>
                  <span class="rank" :class="{ top: index < 3 }">{{ index + 1 }}</span>
                </td>
                <td class="mono">{{ product.id }}</td>
                <td>
                  <div class="bar-cell">
                    <span class="num">{{ product.clicks }}</span>
                    <span class="mini-bar">
                      <i :style="{ width: Math.round((product.clicks / topProducts[0].clicks) * 100) + '%' }"></i>
                    </span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- 趋势占位 -->
    <div class="trend-grid">
      <section class="ui-card">
        <header class="ui-card-h">
          <h3>{{ t.dataAnalysis.translateVolumeTrend }}</h3>
        </header>
        <div class="ui-card-b">
          <div class="chart-ph">Chart placeholder</div>
        </div>
      </section>
      <section class="ui-card">
        <header class="ui-card-h">
          <h3>{{ t.dataAnalysis.apiCostTrend }}</h3>
        </header>
        <div class="ui-card-b">
          <div class="chart-ph">Chart placeholder</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DataAnalysis',
  props: {
    t: Object
  },
  setup() {
    const topProducts = [
      { id: 'P001', clicks: 1250 },
      { id: 'P002', clicks: 980 },
      { id: 'P003', clicks: 875 },
      { id: 'P004', clicks: 750 },
      { id: 'P005', clicks: 625 },
      { id: 'P006', clicks: 580 },
      { id: 'P007', clicks: 450 },
      { id: 'P008', clicks: 380 },
      { id: 'P009', clicks: 320 },
      { id: 'P010', clicks: 280 }
    ]
    
    return {
      topProducts
    }
  }
}
</script>
<style scoped>
/* 日期筛选与仪表盘保持同一套控件外观 */
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

/* 点击量：数字 + 相对占比条，比纯数字更容易比较 */
.bar-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.bar-cell .num {
  width: 54px;
  font-weight: 600;
  color: var(--ink);
}

.mini-bar {
  flex: 1;
  height: 5px;
  max-width: 120px;
  border-radius: 3px;
  background: var(--line-2);
  overflow: hidden;
}

.mini-bar i {
  display: block;
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #5f7ff0, #3b5bdb);
  transition: width var(--t-slow) var(--ease-out);
}

.trend-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--gap);
  margin-top: 16px;
}

/* 图表占位：虚线边框 + 浅底，明确「未接入」而不是坏掉 */
.chart-ph {
  height: 240px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--line-3);
  border-radius: var(--r-sm);
  background: var(--bg-soft);
  color: var(--ink-4);
  font-size: 12px;
  letter-spacing: .04em;
}

@media (max-width: 1024px) {
  .trend-grid { grid-template-columns: 1fr; }
}
</style>
