package com.blog.aispringboot.repository;

import com.blog.aispringboot.entity.SysUsers;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code sys_users} 仓储。账号状态（是否删除、是否锁定）从这里读。
 */
@Repository
public interface SysUsersRepository extends CrudRepository<SysUsers, Long> {
}
