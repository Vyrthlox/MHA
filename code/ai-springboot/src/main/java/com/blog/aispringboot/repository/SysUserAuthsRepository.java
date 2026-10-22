package com.blog.aispringboot.repository;

import com.blog.aispringboot.entity.SysUserAuths;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * {@code sys_user_auths} 仓储。用户名/邮箱唯一，是注册查重与登录查询的入口。
 */
@Repository
public interface SysUserAuthsRepository extends CrudRepository<SysUserAuths, Long> {

    Optional<SysUserAuths> findByUsername(String username);

    Optional<SysUserAuths> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * 更新最后登录时间。
     *
     * <p>Spring Data JDBC 不支持派生的 update 语句，必须手写 SQL；
     * 且带 {@code @Modifying} 的方法<b>必须在事务中调用</b>（见 {@code UserService#login}）。
     */
    @Modifying
    @Query("UPDATE sys_user_auths SET last_login_at = :time WHERE id = :id")
    int updateLastLoginAt(@Param("id") Long id, @Param("time") LocalDateTime time);
}
