# 数据表信息,包含完整SQL语句

## 数据库名称:MHAData 密码:000000

##  账号状态表
CREATE TABLE sys_users (
id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
status       TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '账号状态：1管理员,2普通用户',
locked_until DATETIME DEFAULT NULL COMMENT '临时锁定到期时间',
created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
is_deleted   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号主表';

## 登录认证表（从表）
-- id 与 sys_users.id 是同一个值，且声明为外键。
-- 注意两侧类型必须完全一致：sys_users.id 是 BIGINT UNSIGNED，这里也必须是 BIGINT UNSIGNED。
-- 从表的 id 不是 AUTO_INCREMENT：值由主表回填。
CREATE TABLE sys_user_auths (
id BIGINT UNSIGNED NOT NULL COMMENT '主键，同时作为外键关联 sys_users.id',
username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值',
salt VARCHAR(32) NOT NULL COMMENT '密码盐值',
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
PRIMARY KEY (id),
INDEX idx_email (email),
CONSTRAINT fk_sys_user_auths_user FOREIGN KEY (id) REFERENCES sys_users (id)
    ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录认证表';

-- 若已有表结构需要补齐外键（先对齐类型，再加约束）：
-- ALTER TABLE sys_user_auths MODIFY COLUMN id BIGINT UNSIGNED NOT NULL;
-- ALTER TABLE sys_user_auths
--     ADD CONSTRAINT fk_sys_user_auths_user
--     FOREIGN KEY (id) REFERENCES sys_users (id)
--     ON DELETE CASCADE ON UPDATE RESTRICT;

## 上下文工程（三层记忆）新增表

完整、可直接执行的脚本见 `docker/init/02-memory.sql`。已有数据卷升级时执行：

```bash
docker exec -i mha-mysql mysql -uroot -p000000 MHAData < docker/init/02-memory.sql
# 已有库升级敏感度/保留期字段（幂等）
docker exec -i mha-mysql mysql -uroot -p000000 MHAData < docker/init/03-retention.sql
```

### 用户长期画像 `user_profiles`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | BIGINT UNSIGNED PK | 与 `sys_users.id` 一致，外键级联删除 |
| `summary` | VARCHAR(1000) | 画像摘要，直接拼进系统提示词 |
| `traits_json` | VARCHAR(2000) | 性格、沟通偏好等 JSON |
| `mental_status_json` | VARCHAR(2000) | 心理状况、压力源等 JSON |
| `risk_level` | TINYINT UNSIGNED | 0~5，服务端只升不降 |
| `sensitivity` | VARCHAR(16) | 固定 `important`，画像不参与自动清理 |
| `memory_count` | INT UNSIGNED | 已写入 Milvus 的长期记忆条数 |
| `last_extracted_at` | DATETIME(3) | 最近一次提炼时间 |

### 记忆提炼任务 `memory_extract_tasks`

异步提炼的 outbox：接口线程只写任务，后台线程领取执行。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED AI | 任务 ID |
| `session_id` | VARCHAR(64) UNIQUE | 一个会话只有一条任务，用于防抖 |
| `user_id` | BIGINT UNSIGNED | 外键到 `sys_users` |
| `status` | TINYINT | 0 待处理 / 1 处理中 / 2 完成 / 3 失败 |
| `due_at` | DATETIME(3) | 到期时间（最后一条消息 + 90 秒） |
| `attempts` | INT UNSIGNED | 已尝试次数，最多 3 次 |
| `last_extracted_count` | INT UNSIGNED | 上次提炼时的消息总数 |
| `locked_at` | DATETIME(3) | 领取时间，用于回收进程崩溃后的僵尸任务 |
| `last_error` | VARCHAR(500) | 最近一次失败原因（不含对话正文） |

索引：`(status, due_at)`、`UNIQUE(session_id)`。

### 向量台账 `memory_vectors`

Milvus 的写入与 MySQL 不在同一事务里，台账记录每个 `memory_id` 的归属和状态，
用于重试清理、按用户删除和排查。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `memory_id` | VARCHAR(36) PK | Milvus 文档 ID（用户+正文的 SHA-256 前 32 位） |
| `user_id` | BIGINT UNSIGNED | 归属用户 |
| `session_id` | VARCHAR(64) | 来源会话 |
| `extract_run_id` | VARCHAR(64) | 提炼批次（taskId-attempts） |
| `status` | TINYINT | 0 待写 / 1 已写 / 2 已删 |
| `sensitivity` | VARCHAR(16) | `important` / `normal`；遗留数据默认 `important` |
| `last_recalled_at` | DATETIME(3) | 最近一次被召回的时间 |
| `recall_count` | INT UNSIGNED | 累计召回次数 |
| `retention_until` | DATETIME(3) | 来源会话删除后的清理截止时间；NULL 表示未进入清理期 |

索引：`user_id`、`extract_run_id`、`(sensitivity, status, retention_until)`。

> 记忆正文只存在 Milvus，台账不复制原文，避免敏感信息在 MySQL 里多存一份。