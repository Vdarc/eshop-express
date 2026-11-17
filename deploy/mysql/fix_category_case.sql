-- =====================================================================
-- 修复 product.category 的大小写不一致（3c → 3C）
-- =====================================================================
-- 背景
-- ---------------------------------------------------------------------
-- 问题现象：ES 中 category 的 terms 聚合出现两组 —— 「3C」741 条 + 「3c」3 条，
--          而 MySQL 侧 GROUP BY category 只显示一组「3C」744 条。
--
-- 根因（两层）：
--   1) MySQL 库 collation = utf8mb4_0900_ai_ci，**ci 表示大小写不敏感**。
--      因此 `WHERE category='3c'` 会连 741 条 '3C' 一起返回，
--      `GROUP BY category` 也会把 '3C' 与 '3c' 归成同一组 —— 看不出差异。
--      只有 `WHERE BINARY category='3c'` 才能查出真实的小写记录。
--   2) ES 的 `category` 字段是纯 keyword 类型（无 normalizer），
--      **term 查询天然大小写敏感** —— 于是同一条数据在 MySQL 聚合正常、
--      在 ES 里裂成两组。
--
-- ⚠️ 教训：MySQL 的 ci collation 会掩盖大小写问题，"查不出来" ≠ "不存在"。
--          核对大小写差异必须显式加 BINARY。
-- =====================================================================

-- ---------- 第一步：探查（必须用 BINARY，否则查不出真实差异） ----------
SELECT '-- MySQL collation（注意 ci 后缀 = 不敏感） --' AS note;
SHOW VARIABLES LIKE 'collation_database';

SELECT '-- 大小写不敏感查询（假象）--' AS note;
SELECT COUNT(*) AS insensitive_count FROM product WHERE category = '3c';

SELECT '-- BINARY 强制敏感（真实值）--' AS note;
SELECT COUNT(*) AS lower_3c_count FROM product WHERE BINARY category = '3c';
SELECT COUNT(*) AS upper_3C_count FROM product WHERE BINARY category = '3C';

SELECT '-- 待修正记录明细 --' AS note;
SELECT id, esin, category, user_id, zh_title
  FROM product
 WHERE BINARY category = '3c';


-- ---------- 第二步：修复 ----------
-- 统一为大写 '3C'（与主数据 741 条保持一致）
UPDATE product SET category = '3C' WHERE BINARY category = '3c';


-- ---------- 第三步：验证 ----------
SELECT '-- 修复后应为 0 --' AS note;
SELECT COUNT(*) AS lower_3c_after FROM product WHERE BINARY category = '3c';

SELECT '-- 修复后取值分布 --' AS note;
SELECT category, COUNT(*) AS cnt
  FROM product
 GROUP BY BINARY category
 ORDER BY cnt DESC;

-- ES 侧验证（Canal 会在 1~2 秒内自动同步，无需手动操作）：
--   curl -sk -u elastic:密码 -X POST "https://localhost:9200/product/_search" \
--        -H "Content-Type: application/json" \
--        -d '{"size":0,"aggs":{"cat":{"terms":{"field":"category"}}}}'
--   期望：只剩 clothes / 3C 两组


-- ---------- 附：如何避免复发（长期建议，未实施） ----------
-- 方案 A（推荐）：ES mapping 给 category 加 lowercase normalizer，
--                使 ES 侧也变得大小写不敏感，与 MySQL 行为一致。
--                代价：需重建索引（recreate + 全量同步）。
--
--      "category": {
--        "type": "keyword",
--        "normalizer": "lc_normalizer"
--      }
--      # settings.analysis.normalizer.lc_normalizer = { type: lowercase }
--
-- 方案 B：在应用层写入前统一 normalize（如 category.strip().upper()），
--        从源头保证只有一种写法。
--
-- 方案 C：给 product.category 加 CHECK 约束或改用 ENUM 类型，物理上杜绝。
