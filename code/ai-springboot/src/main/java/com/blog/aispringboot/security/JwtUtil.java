package com.blog.aispringboot.security;

import com.blog.aispringboot.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析。
 */
@Component
public class JwtUtil {

    private final JwtProperties props;
    private SecretKey key;

    public JwtUtil(JwtProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        // 密钥不足 32 字节时这里会抛 WeakKeyException —— 启动即失败，
        // 好过运行期静默降级成一个不安全的签名。
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 签发 token。
     *
     * <p>用户 id 放在 {@code sub} 里以字符串承载，解析端用 {@code Long.valueOf} 还原。
     * 不用 {@code claims.get("userId", Long.class)}——那依赖 jjwt 在 JSON Integer/Long
     * 之间的数值强制转换行为。
     */
    public String generateToken(Long userId, String username, Integer status) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("status", status)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + props.getExpireSeconds() * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并验签。
     *
     * @throws io.jsonwebtoken.ExpiredJwtException token 已过期
     * @throws io.jsonwebtoken.JwtException        签名不合法或结构损坏
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
