package com.blog.aispringboot.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 登录认证表 {@code sys_user_auths}。
 *
 * <p>id 与 {@link SysUsers#getId()} 是同一个值，并在数据库层面声明为外键
 * （{@code fk_sys_user_auths_user}，{@code ON DELETE CASCADE}）。
 * 因此这条记录必须<b>在主表之后</b>插入，且 id 不能由本表自增生成。
 */
@Data
@NoArgsConstructor
@Table("sys_user_auths")
public class SysUserAuths {

    /** 与 sys_users.id 保持一致；列类型为 BIGINT UNSIGNED（必须与外键目标一致） */
    @Id
    private Long id;

    @Column("username")
    private String username;

    @Column("email")
    private String email;

    /** BCrypt 哈希串，形如 {@code $2a$10$...} */
    @Column("password_hash")
    private String passwordHash;

    /**
     * 该列在表结构里是 NOT NULL，但语义上并不使用。
     *
     * <p>BCrypt 已把随机盐内嵌在 {@link #passwordHash} 中，这里只写一个固定占位符。
     * 不要写入随机生成的盐——那会造成「盐真的参与运算」的误解。
     */
    @Column("salt")
    private String salt;

    /** 由数据库维护，Java 侧只读不写 */
    @ReadOnlyProperty
    @Column("created_at")
    private LocalDateTime createdAt;

    /** 由数据库维护，Java 侧只读不写 */
    @ReadOnlyProperty
    @Column("updated_at")
    private LocalDateTime updatedAt;

    /** 登录成功后更新 */
    @Column("last_login_at")
    private LocalDateTime lastLoginAt;
}
