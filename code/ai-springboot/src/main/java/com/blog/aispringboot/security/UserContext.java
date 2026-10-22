package com.blog.aispringboot.security;

/**
 * 当前请求的登录用户上下文。
 *
 * <p>用 {@link ThreadLocal} 承载，因此<b>必须在请求结束时清理</b>
 * （见 {@code JwtAuthInterceptor#afterCompletion}）。Tomcat 会复用线程，
 * 不清理会把一个用户的身份串到下一个用户的请求上。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
