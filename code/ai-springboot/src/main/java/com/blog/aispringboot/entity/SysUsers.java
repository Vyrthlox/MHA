package com.blog.aispringboot.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 账号主表 {@code sys_users}。
 *
 * <p>只存账号本身的状态；用户名/密码/邮箱在 {@link SysUserAuths}，两表通过同一个 id 关联。
 * 这是<b>主表</b>：{@code sys_user_auths.id} 上有指向本表的外键
 * （{@code ON DELETE CASCADE}），删除账号时凭据会自动跟着删。
 */
@Data
@NoArgsConstructor
@Table("sys_users")
public class SysUsers {

    @Id
    private Long id;

    /** 1=管理员，2=普通用户。注册时必须显式写 2，不要依赖数据库默认值。 */
    @Column("status")
    private Integer status;

    /** 临时锁定到期时间，为空表示未锁定 */
    @Column("locked_until")
    private LocalDateTime lockedUntil;

    /** 由数据库维护（DEFAULT CURRENT_TIMESTAMP），Java 侧只读不写 */
    @ReadOnlyProperty
    @Column("created_at")
    private LocalDateTime createdAt;

    /** 由数据库维护（ON UPDATE CURRENT_TIMESTAMP），Java 侧只读不写 */
    @ReadOnlyProperty
    @Column("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=否，1=是。
     *
     * <p>用 {@code Integer} 而非 {@code Boolean}，并显式标注列名：Spring Data 的属性名
     * 归一化可能会把 {@code is} 前缀剥掉，导致映射到不存在的 {@code deleted} 列。
     */
    @Column("is_deleted")
    private Integer isDeleted;
}
