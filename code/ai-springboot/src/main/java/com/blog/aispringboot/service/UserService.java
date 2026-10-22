package com.blog.aispringboot.service;

import com.blog.aispringboot.common.BusinessException;
import com.blog.aispringboot.dto.LoginRequest;
import com.blog.aispringboot.dto.LoginResponse;
import com.blog.aispringboot.dto.RegisterRequest;
import com.blog.aispringboot.dto.SendCodeResponse;
import com.blog.aispringboot.dto.UserInfo;
import com.blog.aispringboot.entity.SysUserAuths;
import com.blog.aispringboot.entity.SysUsers;
import com.blog.aispringboot.repository.SysUserAuthsRepository;
import com.blog.aispringboot.repository.SysUsersRepository;
import com.blog.aispringboot.security.JwtUtil;
import com.blog.aispringboot.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户注册与登录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** sys_users.status：1=管理员 */
    private static final int STATUS_ADMIN = 1;
    /** sys_users.status：2=普通用户 */
    private static final int STATUS_NORMAL_USER = 2;

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    /**
     * salt 列的占位值。该列 NOT NULL 但 BCrypt 不使用它（盐已内嵌在哈希串里）。
     * 写固定值而非随机值，避免造成「这个盐真的参与运算」的误解。
     */
    private static final String SALT_PLACEHOLDER = "bcrypt$embedded";

    private final SysUsersRepository sysUsersRepository;
    private final SysUserAuthsRepository sysUserAuthsRepository;
    private final JdbcAggregateTemplate jdbcAggregateTemplate;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 发送注册验证码。
     *
     * <p>「邮箱是否已被注册」这个判断放在这里而不是 {@code EmailCodeService} 里：
     * 那是个业务规则（注册场景），而验证码服务本身只该管验证码的生成与校验，
     * 不该反过来依赖用户仓储。这样将来加「找回密码」时，同一个 EmailCodeService
     * 可以直接复用，规则各不相同。
     */
    public SendCodeResponse sendRegisterCode(String email) {
        if (sysUserAuthsRepository.existsByEmail(email)) {
            throw new BusinessException("该邮箱已被注册");
        }
        return emailCodeService.send(email);
    }

    /**
     * 注册。
     *
     * <p>{@code sys_user_auths.id} 已是 {@code sys_users.id} 的外键（{@code ON DELETE CASCADE}），
     * id 的一致性由数据库兜底。但整个方法**仍然必须**在一个事务里：
     * 第二张表插入失败时要回滚掉第一张表，否则会留下一个没有任何凭据的孤儿账号
     * （外键只能阻止「凭据指向不存在的账号」，管不了「账号没有凭据」）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest req) {
        // 1) 两次密码一致
        if (!Objects.equals(req.getPassword(), req.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        // 2) 查重
        if (sysUserAuthsRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("用户名已被注册");
        }
        if (sysUserAuthsRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        // 3) 校验并消费验证码。
        // 放在两个查重之后是有意的：反过来的话，「用户名已被注册」会白白烧掉一个验证码，
        // 用户得等满 60 秒冷却才能重新获取——一个纯属浪费的等待。
        // 这样做不引入新的信息泄露：这个接口本来就会返回「用户名已被注册」。
        //
        // 注意「消费」是删 Redis 键，它不参与下面这个数据库事务：若验证码校验通过、
        // 但后续插库失败回滚，验证码已经被消耗掉了，用户需要重新获取一个。
        // 这是刻意的取舍——反过来（先插库、提交后再删码）会出现「响应丢了但码还在」
        // 的可重放窗口，代价更大。
        emailCodeService.verifyAndConsume(req.getEmail(), req.getCode());

        // 4) 先插主表拿到自增主键
        SysUsers user = new SysUsers();
        // 显式写 2，不依赖列默认值。DDL 与线上表的默认值现在都是 2，
        // 但默认值随时可能被改，而「新注册的人不能是管理员」是安全属性，
        // 不该押在它上面。
        user.setStatus(STATUS_NORMAL_USER);
        user.setIsDeleted(NOT_DELETED);
        // lockedUntil / createdAt / updatedAt 一律不写，交给数据库
        SysUsers saved = sysUsersRepository.save(user);

        Long userId = saved.getId();
        if (userId == null) {
            throw new IllegalStateException("sys_users 主键回填失败");
        }

        // 5) 用同一个 id 插认证表
        SysUserAuths auth = new SysUserAuths();
        auth.setId(userId);
        auth.setUsername(req.getUsername());
        auth.setEmail(req.getEmail());
        auth.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        auth.setSalt(SALT_PLACEHOLDER);
        // 必须用 insert 而不是 save：save() 依据 isNew() 决定 INSERT 还是 UPDATE，
        // 而默认的 isNew() 判定是「id 为 null」。这里为了让两表 id 一致而预先设了 id，
        // save() 会误判为已存在的实体去发 UPDATE —— 影响 0 行且不抛异常，
        // 最终事务照常提交，留下一个没有任何凭据的孤儿 sys_users 行。
        jdbcAggregateTemplate.insert(auth);

        log.info("用户注册成功: id={}, username={}", userId, req.getUsername());
        return userId;
    }

    /**
     * 登录。
     *
     * <p><b>校验顺序是有意为之</b>：先验密码，再查账号状态。反过来的话，
     * 攻击者不用知道密码就能通过「账号被锁定」这类提示确认某个账号是否存在。
     * 先验密码使得密码错误时所有失败路径都返回同一句话。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest req) {
        String account = req.getUsername() == null ? "" : req.getUsername().trim();

        // 1) 用户名或邮箱均可登录
        SysUserAuths auth = sysUserAuthsRepository.findByUsername(account)
                .or(() -> sysUserAuthsRepository.findByEmail(account))
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        // 2) 先校验密码
        if (!passwordEncoder.matches(req.getPassword(), auth.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 3) 账号状态在主表里，必须再查一次
        SysUsers user = sysUsersRepository.findById(auth.getId())
                .orElseThrow(() -> new BusinessException("账号状态异常"));

        if (Integer.valueOf(DELETED).equals(user.getIsDeleted())) {
            throw new BusinessException("账号已被删除");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("账号已被锁定，请于 " + user.getLockedUntil() + " 之后重试");
        }

        // 4) 更新最后登录时间
        sysUserAuthsRepository.updateLastLoginAt(auth.getId(), LocalDateTime.now());

        // 5) 签发 token
        String token = jwtUtil.generateToken(user.getId(), auth.getUsername(), user.getStatus());

        log.info("用户登录成功: id={}, username={}", user.getId(), auth.getUsername());
        return new LoginResponse(token, toUserInfo(user, auth));
    }

    /**
     * 当前登录用户信息（供受保护接口使用，同时用于验证拦截器与 UserContext）。
     */
    public UserInfo currentUserInfo() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        SysUsers user = sysUsersRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("账号不存在"));
        SysUserAuths auth = sysUserAuthsRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("账号不存在"));
        return toUserInfo(user, auth);
    }

    private UserInfo toUserInfo(SysUsers user, SysUserAuths auth) {
        return new UserInfo(
                user.getId(),
                auth.getUsername(),
                auth.getEmail(),
                user.getStatus(),
                user.getStatus() != null && user.getStatus() == STATUS_ADMIN ? "管理员" : "普通用户");
    }
}
