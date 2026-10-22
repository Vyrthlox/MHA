-- 上下文工程（三层记忆）新增结构。
-- 该脚本是幂等的，可对已有 MHAData 库重复执行：
--   docker exec -i mha-mysql mysql -uroot -p000000 MHAData < docker/init/02-memory.sql

CREATE TABLE IF NOT EXISTS user_profiles (
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    summary             VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '画像摘要，直接进入提示词',
    traits_json         VARCHAR(2000) NOT NULL DEFAULT '{}' COMMENT '性格/沟通偏好等 JSON',
    mental_status_json  VARCHAR(2000) NOT NULL DEFAULT '{}' COMMENT '心理状况 JSON',
    risk_level          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '风险等级 0-5，只升不降',
    sensitivity         VARCHAR(16) NOT NULL DEFAULT 'important' COMMENT 'important/normal；画像固定 important，不参与自动清理',
    memory_count        INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已写入的长期记忆条数',
    last_extracted_at   DATETIME(3) NULL COMMENT '最近一次提炼时间',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES sys_users (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户长期画像';

CREATE TABLE IF NOT EXISTS memory_extract_tasks (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id          VARCHAR(64) NOT NULL COMMENT '会话ID',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    status              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2完成 3失败',
    due_at              DATETIME(3) NOT NULL COMMENT '到期时间（防抖）',
    attempts            INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已尝试次数',
    last_extracted_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上次提炼时的消息总数',
    locked_at           DATETIME(3) NULL COMMENT '领取时间，用于回收僵尸任务',
    last_error          VARCHAR(500) NULL COMMENT '最近一次失败原因（不含正文）',
    created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_session (session_id),
    KEY idx_due (status, due_at),
    CONSTRAINT fk_met_user FOREIGN KEY (user_id) REFERENCES sys_users (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆提炼任务（outbox）';

CREATE TABLE IF NOT EXISTS memory_vectors (
    memory_id     VARCHAR(36) NOT NULL COMMENT 'Milvus 文档ID（UUID）',
    user_id       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    session_id    VARCHAR(64) NOT NULL COMMENT '来源会话',
    extract_run_id VARCHAR(64) NOT NULL COMMENT '提炼批次ID，用于重试时清理半成品',
    status        TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0待写 1已写 2已删',
    sensitivity   VARCHAR(16) NOT NULL DEFAULT 'important'
                  COMMENT 'important/normal；遗留数据默认 important，避免被误清理',
    last_recalled_at DATETIME(3) NULL COMMENT '最近一次被召回的时间',
    recall_count  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计召回次数',
    retention_until DATETIME(3) NULL
                  COMMENT '来源会话删除后的清理截止时间；NULL 表示未进入清理期',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (memory_id),
    KEY idx_user (user_id),
    KEY idx_run (extract_run_id),
    KEY idx_retention (sensitivity, status, retention_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='长期记忆向量台账';
