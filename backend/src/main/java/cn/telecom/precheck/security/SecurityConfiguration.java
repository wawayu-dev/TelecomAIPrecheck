package cn.telecom.precheck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.telecom.precheck.web.ApiExceptionHandler.ApiError;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService demoUsers(Environment env) {
        var users = new ArrayList<UserDetails>();
        var encoder = new BCryptPasswordEncoder();
        for (String role : List.of("OPERATOR", "REVIEWER", "AUDITOR", "HOST")) {
            String password = env.getProperty("DEMO_" + role + "_PASSWORD", "");
            if (!password.isBlank()) {
                if (password.length() < 12) throw new IllegalArgumentException("Demo password must be at least 12 characters");
                users.add(User.withUsername(role.toLowerCase(Locale.ROOT)).password("{bcrypt}"+encoder.encode(password)).roles(role).build());
            }
        }
        return new InMemoryUserDetailsManager(users);
    }
    @Bean @Profile("mysql")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain secured(HttpSecurity http, ObjectMapper json, Environment env) throws Exception {
        boolean demoLogin = env.getProperty("precheck.demo-login.enabled", Boolean.class, false);
        http.authorizeHttpRequests(a -> {
                a.requestMatchers("/actuator/health").permitAll();
                if (demoLogin) a.requestMatchers("/api/demo-session").permitAll();
                a.requestMatchers("/api/internal/ticket-events/**").hasRole("HOST")
                    .anyRequest().hasAnyRole("OPERATOR","REVIEWER","AUDITOR");
            })
            .csrf(c -> {
                c.ignoringRequestMatchers("/api/internal/ticket-events/**");
                if (demoLogin) c.ignoringRequestMatchers("/api/demo-session");
            })
            .httpBasic(b -> b.authenticationEntryPoint((req,res,e) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                if (!"XMLHttpRequest".equals(req.getHeader("X-Requested-With")))
                    res.setHeader("WWW-Authenticate", "Basic realm=precheck");
                json.writeValue(res.getWriter(),new ApiError("UNAUTHENTICATED","请先登录",(String)req.getAttribute("traceId")));
            }))
            .logout(l -> l.logoutUrl("/api/session/logout")
                .invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,auth) -> res.setStatus(204)))
            .exceptionHandling(e -> e.accessDeniedHandler((req,res,ex) -> {
                res.setStatus(403); res.setContentType("application/json;charset=UTF-8");
                json.writeValue(res.getWriter(),new ApiError("FORBIDDEN","无权限或 CSRF 校验失败",(String)req.getAttribute("traceId")));
            }));
        return http.build();
    }
    @Bean @Profile("!mysql")
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain contextOnly(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable()).build();
    }
    @RestController @Profile("mysql")
    public static class SessionController {
        @GetMapping("/api/session")
        public Map<String,Object> session(CsrfToken csrf, org.springframework.security.core.Authentication auth) {
            return response(csrf, auth);
        }
        private Map<String,Object> response(CsrfToken csrf, org.springframework.security.core.Authentication auth) {
            return Map.of("actorId",auth.getName(),"roles",auth.getAuthorities().stream().map(Object::toString).toList(),
                "csrfHeader",csrf.getHeaderName(),"csrfToken",csrf.getToken());
        }
    }
    /** 仅供合成数据演示环境使用；生产宿主必须关闭并接入其统一认证。 */
    @RestController @Profile("mysql")
    @ConditionalOnProperty(prefix="precheck.demo-login",name="enabled",havingValue="true")
    public static class DemoSessionController {
        private static final Map<String,String> ROLES = Map.of(
            "operator","OPERATOR", "reviewer","REVIEWER", "auditor","AUDITOR");
        private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
        @PostMapping("/api/demo-session")
        public Map<String,Object> login(@RequestBody Map<String,String> body, CsrfToken csrf,
                HttpServletRequest request, HttpServletResponse response) {
            String account = body.get("account"), role = ROLES.get(account);
            if (role == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported demo account");
            var authentication = new UsernamePasswordAuthenticationToken(account, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.getSession(true);
            request.changeSessionId();
            contexts.saveContext(context, request, response);
            return Map.of("actorId",account,"roles",List.of("ROLE_" + role),
                "csrfHeader",csrf.getHeaderName(),"csrfToken",csrf.getToken());
        }
    }
}
