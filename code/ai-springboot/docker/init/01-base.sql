-- 基础账号表（与 docs/table.md 保持一致）。
-- Docker 首次初始化时执行；已有数据卷不会重复执行，升级请手动执行 02-memory.sql。
CREATE TABLE IF NOT EXISTS sys_users (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
    status       TINYINT UNSIGNED NOT NULL DEFAULT 2 COMMENT '账号状态：1管理员,2普通用户',
    locked_until DATETIME DEFAULT NULL COMMENT '临时锁定到期时间',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0否 1是',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号主表';

CREATE TABLE IF NOT EXISTS sys_user_auths (
    id            BIGINT UNSIGNED NOT NULL COMMENT '主键，同时作为外键关联 sys_users.id',
    username      VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    email         VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希值',
    salt          VARCHAR(32) NOT NULL COMMENT '密码盐值',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_at TIMESTAMP NULL COMMENT '最后登录时间',
    PRIMARY KEY (id),
    INDEX idx_email (email),
    CONSTRAINT fk_sys_user_auths_user FOREIGN KEY (id) REFERENCES sys_users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录认证表';
