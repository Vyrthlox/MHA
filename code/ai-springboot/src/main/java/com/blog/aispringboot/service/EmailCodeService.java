package com.blog.aispringboot.service;

import com.blog.aispringboot.common.BusinessException;
import com.blog.aispringboot.config.EmailCodeProperties;
import com.blog.aispringboot.dto.SendCodeResponse;
import com.blog.aispringboot.mail.MailDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 邮箱验证码的生成、存储与校验。
 *
 * <p>全部状态放在 Redis，用三个键承载：
 * <pre>
 *   mha:emailcode:register:&lt;email&gt;   验证码明文     TTL = expireSeconds
 *   mha:emailcode:cooldown:&lt;email&gt;   冷却标记 "1"   TTL = cooldownSeconds
 *   mha:emailcode:fail:&lt;email&gt;       失败计数       TTL = expireSeconds
 * </pre>
 *
 * <p><b>「5 分钟内重发则旧验证码作废」不需要任何额外逻辑</b>——新验证码直接覆盖同一个键，
 * 旧值就没了。TTL 和覆盖写把「过期」与「作废」两个需求免费实现了，这是选 Redis 而不是
 * MySQL 建表的主要理由。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final String KEY_REGISTER = "mha:emailcode:register:";
    private static final String KEY_COOLDOWN = "mha:emailcode:cooldown:";
    private static final String KEY_FAIL = "mha:emailcode:fail:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final EmailCodeProperties props;
    private final MailDelivery mailDelivery;

    /**
     * 发送注册验证码。
     *
     * <p>调用方需先确认该邮箱尚未注册（见 {@code UserService#sendRegisterCode}）。
     *
     * @throws BusinessException 冷却中、或邮件投递失败
     */
    public SendCodeResponse send(String email) {
        String cooldownKey = KEY_COOLDOWN + email;

        // 先抢占冷却位再发信，而不是发完再标记。
        // setIfAbsent + TTL 是一次原子操作：两个并发请求只有一个能抢到，
        // 后到的那个走下面的分支。若写成「先 get 判断、再 set」，
        // 两个请求可能都通过判断，和注册那里的 existsByUsername 是同一类竞态。
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(props.getCooldownSeconds()));
        if (!Boolean.TRUE.equals(acquired)) {
            Long remain = redis.getExpire(cooldownKey);
            throw new BusinessException("发送过于频繁，请 " + formatRemain(remain) + " 秒后重试");
        }

        String code = randomCode();
        try {
            // 覆盖写 = 旧验证码立即作废
            redis.opsForValue().set(KEY_REGISTER + email, code,
                    Duration.ofSeconds(props.getExpireSeconds()));
            // 新码配新的失败计数，避免上一轮的错误次数带到这一轮
            redis.delete(KEY_FAIL + email);

            mailDelivery.send(email, "【心理健康助手】注册验证码",
                    "您的注册验证码是：" + code + "\n"
                            + "有效期 " + (props.getExpireSeconds() / 60) + " 分钟，请勿泄露给他人。\n"
                            + "若非本人操作，请忽略本邮件。");
        } catch (Exception e) {
            // 信没发出去就不该让用户干等 60 秒。注意这里只回滚冷却标记，
            // 不改动验证码本身——投递失败时用户拿不到码，留着也无害，且下次发送会覆盖它。
            redis.delete(cooldownKey);
            log.error("验证码邮件投递失败: email={}", email, e);
            throw new BusinessException("邮件发送失败，请稍后重试");
        }

        log.info("注册验证码已发送: email={}", email);
        return new SendCodeResponse(props.getExpireSeconds(), props.getCooldownSeconds());
    }

    /**
     * 校验并消费验证码。通过则返回；否则抛 {@link BusinessException}。
     *
     * <p>校验成功会立即删除该验证码——它是<b>一次性</b>的，防止同一个码被重复使用。
     */
    public void verifyAndConsume(String email, String inputCode) {
        String codeKey = KEY_REGISTER + email;
        String expected = redis.opsForValue().get(codeKey);

        if (expected == null) {
            // 「从没发过」和「已过期（含被新码覆盖后旧码的 TTL）」对用户是同一件事
            throw new BusinessException("验证码已过期，请重新获取");
        }

        if (!expected.equals(inputCode)) {
            handleWrongCode(email, codeKey);
        }

        redis.delete(codeKey);
        redis.delete(KEY_FAIL + email);
    }

    /**
     * 记一次失败并决定是否作废。
     *
     * <p>失败次数限制是有必要的：6 位数字码只有 100 万种组合，在 5 分钟窗口内不限制次数
     * 就存在被爆破的可能。短 TTL + 失败次数限制才是这套机制真正的防线
     * （而不是把验证码哈希存储）。
     */
    private void handleWrongCode(String email, String codeKey) {
        String failKey = KEY_FAIL + email;
        Long fails = redis.opsForValue().increment(failKey);

        // increment 一个不存在的键会把它创建为 1 且<b>不带 TTL</b>，
        // 不补这一下就会留下永不过期的垃圾键。只在首次失败时设。
        if (fails != null && fails == 1L) {
            redis.expire(failKey, Duration.ofSeconds(props.getExpireSeconds()));
        }

        if (fails != null && fails >= props.getMaxAttempts()) {
            redis.delete(codeKey);
            redis.delete(failKey);
            log.warn("验证码错误次数超限，已作废: email={}, failCount={}", email, fails);
            throw new BusinessException("验证码错误次数过多，请重新获取");
        }

        throw new BusinessException("验证码不正确");
    }

    /** 生成指定位数的数字验证码，用 SecureRandom 而非 Random（后者可被预测）。 */
    private String randomCode() {
        int bound = 1;
        for (int i = 0; i < props.getLength(); i++) {
            bound *= 10;
        }
        return String.format("%0" + props.getLength() + "d", RANDOM.nextInt(bound));
    }

    private String formatRemain(Long remainSeconds) {
        // getExpire 在键存在时返回剩余秒数。理论上不会走到 -1，但别把负数显示给用户。
        return String.valueOf(remainSeconds == null || remainSeconds < 1 ? 1 : remainSeconds);
    }
}
