package cn.telecom.precheck.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;

/** 身份只能来自宿主认证后的 SecurityContext；不能由请求 DTO/自定义 Header 指定。 */
public final class CurrentActor {
    private CurrentActor() {}
    public static String id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            throw new AccessDeniedException("Authentication required");
        String name = auth.getName();
        // 集成契约要求稳定的不透明用户 ID，不接受手机号、身份证或邮箱作审计身份。
        if (!name.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) throw new AccessDeniedException("Opaque actor ID required");
        return name;
    }
}
