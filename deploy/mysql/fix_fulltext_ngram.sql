-- ============================================================================
-- 商品搜索全文索引修复：默认解析器 → ngram 解析器
--
-- 【重要】不要用 ALTER TABLE ... DROP INDEX + ADD FULLTEXT INDEX 的方式
--   实测（MySQL 8.0.32）该方式会失败，报出误导性的错误：
--     ERROR 1062: Duplicate entry 'NULL-NULL-NULL-...' for key 'product.ft_product_search'
--     ERROR 1062: Duplicate entry '0' for key 'product.idx_product_user_id'
--   经排查，这两处"重复"均不存在：
--     · 16 个检索列全为 NULL 的行数 = 0
--     · idx_product_user_id 是非唯一索引（NON_UNIQUE=1），本身允许重复
--   且 ALTER TABLE product FORCE 也无法绕过。
--   根因是 InnoDB 在【已有数据且有外键】的表上原地新增 FULLTEXT 索引时，
--   创建隐式 FTS_DOC_ID 会失败。而"先建空表再灌数据"的路径验证可行。
--
--   因此正确做法是【建新表 → 灌数据 → 原子重命名】。
--
-- 实测效果（2382 商品，ngram_token_size=2）：
--   关键词    修复前   修复后   LIKE 基准
--   纯棉         0      33       33
--   T恤          0      28       28
--   连衣裙       5      38       38
--   透气         0     380      380
--   速干         0      56       56
--   防晒         0      15       15
--   耳机         0      65       65
--   cotton     371     374      374
--   dress      212     340      340
--   → ngram 召回与 LIKE 完全一致，且无任何关键词下降。
--
-- 执行前请务必备份：
--   mysqldump -u root -p ecommerce product > product_backup.sql
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 步骤 0：执行前留档
-- ----------------------------------------------------------------------------
SELECT INDEX_NAME, COLUMN_NAME, INDEX_TYPE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'product'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT COUNT(*) AS product_rows FROM product;

-- ----------------------------------------------------------------------------
-- 步骤 1：准备替代表（无损，不动现表）
--
-- CREATE TABLE ... LIKE 会复制列定义与全部索引，但不复制外键，需在步骤 2 补回。
-- 注意：必须先建好 FULLTEXT 索引再灌数据，反之会触发上面提到的 1062 错误。
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS product_new;

CREATE TABLE product_new LIKE product;

ALTER TABLE product_new
  ADD FULLTEXT INDEX ft_product_search (
    zh_title, zh_desc,
    en_title, en_desc,
    ar_title, ar_desc,
    fr_title, fr_desc,
    de_title, de_desc,
    ja_title, ja_desc,
    ko_title, ko_desc,
    ru_title, ru_desc
  ) WITH PARSER ngram;

-- ----------------------------------------------------------------------------
-- 步骤 2：补回外键
--
-- MySQL 的外键约束名是【库级唯一】的，product 已占用 fk_product_user，
-- 因此替代表必须换名。功能完全等价（同为 RESTRICT/RESTRICT）。
-- ----------------------------------------------------------------------------
ALTER TABLE product_new
  ADD CONSTRAINT fk_product_user_ngram
  FOREIGN KEY (user_id) REFERENCES sys_users (user_id)
  ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ----------------------------------------------------------------------------
-- 步骤 3：灌数据并核对
-- ----------------------------------------------------------------------------
INSERT INTO product_new SELECT * FROM product;

SELECT (SELECT COUNT(*) FROM product)     AS old_rows,
       (SELECT COUNT(*) FROM product_new) AS new_rows;

-- 期望两者相等；不相等则停止，不要执行步骤 4

-- ----------------------------------------------------------------------------
-- 步骤 4：切换到新表（原子操作，瞬时完成）
--
-- 旧表保留为 product_backup_pre_ngram，便于回滚；确认无误后再手动删除。
-- ----------------------------------------------------------------------------
RENAME TABLE product TO product_backup_pre_ngram,
             product_new TO product;

-- ----------------------------------------------------------------------------
-- 步骤 5：验证修复效果（期望：纯棉 33、连衣裙 38、透气 380）
-- ----------------------------------------------------------------------------
SELECT '纯棉' AS kw, COUNT(*) AS hits FROM product
  WHERE MATCH(zh_title, zh_desc, en_title, en_desc, ar_title, ar_desc,
              fr_title, fr_desc, de_title, de_desc, ja_title, ja_desc,
              ko_title, ko_desc, ru_title, ru_desc)
        AGAINST('+纯棉' IN BOOLEAN MODE)
UNION ALL
SELECT '连衣裙', COUNT(*) FROM product
  WHERE MATCH(zh_title, zh_desc, en_title, en_desc, ar_title, ar_desc,
              fr_title, fr_desc, de_title, de_desc, ja_title, ja_desc,
              ko_title, ko_desc, ru_title, ru_desc)
        AGAINST('+连衣裙' IN BOOLEAN MODE)
UNION ALL
SELECT '透气', COUNT(*) FROM product
  WHERE MATCH(zh_title, zh_desc, en_title, en_desc, ar_title, ar_desc,
              fr_title, fr_desc, de_title, de_desc, ja_title, ja_desc,
              ko_title, ko_desc, ru_title, ru_desc)
        AGAINST('+透气' IN BOOLEAN MODE);

SHOW CREATE TABLE product;   -- 确认已带 WITH PARSER ngram

-- ----------------------------------------------------------------------------
-- 步骤 6：确认无误后清理备份表（会释放 fk_product_user 约束名）
-- ----------------------------------------------------------------------------
-- DROP TABLE product_backup_pre_ngram;

-- 若希望约束名回归 fk_product_user（纯命名一致性，非功能需要）：
-- ALTER TABLE product DROP FOREIGN KEY fk_product_user_ngram;
-- ALTER TABLE product ADD CONSTRAINT fk_product_user
--   FOREIGN KEY (user_id) REFERENCES sys_users (user_id)
--   ON DELETE RESTRICT ON UPDATE RESTRICT;

-- ============================================================================
-- 回滚方案：把旧表换回来
-- ============================================================================
-- RENAME TABLE product TO product_failed_ngram,
--              product_backup_pre_ngram TO product;
-- DROP TABLE product_failed_ngram;

-- ============================================================================
-- 配套代码改动（仅修索引不足以完全修好搜索）
-- ============================================================================
-- 索引只解决【召回】。以下缺陷需同时修复：
--
-- 1. 相关性排序丢失
--    backend/dao/impl/ProductDAOImpl.py:82-91
--    取完 id 后用 id.in_() 重查，IN 不保证顺序，ORDER BY score DESC 被丢弃。
--    实测：SQL 相关性 Top1 = B0FCSKL81C，DAO 实际返回首条 = B01K2CMFVM。
--    → 应在 SQL 内直接 ORDER BY score DESC 分页返回。
--
-- 2. 搜索分支无分页
--    backend/services/product_service.py get_product_list 搜索分支
--    硬编码 {"pages": 1, "current": 1} 且忽略传入的 page 参数；
--    而前端 SearchResults.vue 有分页 UI，翻页会追加重复数据。
--
-- 3. BOOLEAN 查询用 AND 语义过严
--    现有代码把关键词拼成 '+词1 +词2'（AND）。用户输入「纯棉连衣裙」这类
--    不带空格的长串时，ngram 会切成 纯棉/棉连/连衣/衣裙 等 bigram，
--    AND 要求全部命中，极易返回 0 条。
--    → 搜索框场景应改用 OR 语义（去掉 '+' 前缀），由 score 排序决定相关性。
-- ============================================================================
