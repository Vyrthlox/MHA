-- 记忆敏感度与保留期迁移（幂等）。
-- 新库由 02-memory.sql 直接建好；已有的 MHAData 库执行：
--   docker exec -i mha-mysql mysql -uroot -p000000 MHAData < docker/init/03-retention.sql
--
-- 说明：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，这里用 information_schema
-- 判断后动态执行，重复运行不会报错。

-- user_profiles.sensitivity：画像固定 important
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'user_profiles'
       AND column_name = 'sensitivity') = 0,
    'ALTER TABLE user_profiles ADD COLUMN sensitivity VARCHAR(16) NOT NULL DEFAULT ''important'' COMMENT ''important/normal；画像固定 important，不参与自动清理'' AFTER risk_level',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- memory_vectors.sensitivity：遗留数据默认 important，宁可少删不可误删
SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'memory_vectors'
       AND column_name = 'sensitivity') = 0,
    'ALTER TABLE memory_vectors ADD COLUMN sensitivity VARCHAR(16) NOT NULL DEFAULT ''important'' COMMENT ''important/normal；遗留数据默认 important，避免被误清理'' AFTER status',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'memory_vectors'
       AND column_name = 'last_recalled_at') = 0,
    'ALTER TABLE memory_vectors ADD COLUMN last_recalled_at DATETIME(3) NULL COMMENT ''最近一次被召回的时间'' AFTER sensitivity',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'memory_vectors'
       AND column_name = 'recall_count') = 0,
    'ALTER TABLE memory_vectors ADD COLUMN recall_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''累计召回次数'' AFTER last_recalled_at',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'memory_vectors'
       AND column_name = 'retention_until') = 0,
    'ALTER TABLE memory_vectors ADD COLUMN retention_until DATETIME(3) NULL COMMENT ''来源会话删除后的清理截止时间；NULL 表示未进入清理期'' AFTER recall_count',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'memory_vectors'
       AND index_name = 'idx_retention') = 0,
    'ALTER TABLE memory_vectors ADD KEY idx_retention (sensitivity, status, retention_until)',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
