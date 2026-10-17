package cn.telecom.precheck.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cn.telecom.precheck.web.ApiExceptionHandler.ApiError;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService demoUsers(Environment env) {
        var users = new ArrayList<UserDetails>();
        var encoder = new BCryptPasswordEncoder();
        for (String role : List.of("OPERATOR", "REVIEWER", "AUDITOR")) {
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
    SecurityFilterChain secured(HttpSecurity http, ObjectMapper json) throws Exception {
        http.authorizeHttpRequests(a -> a.requestMatchers("/actuator/health").permitAll()
                .anyRequest().hasAnyRole("OPERATOR","REVIEWER","AUDITOR"))
            .httpBasic(b -> b.authenticationEntryPoint((req,res,e) -> {
                res.setStatus(401); res.setContentType("application/json;charset=UTF-8");
                res.setHeader("WWW-Authenticate", "Basic realm=precheck");
                json.writeValue(res.getWriter(),new ApiError("UNAUTHENTICATED","请先登录",(String)req.getAttribute("traceId")));
            }))
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
            return Map.of("actorId",CurrentActor.id(),"roles",auth.getAuthorities().stream().map(Object::toString).toList(),
                "csrfHeader",csrf.getHeaderName(),"csrfToken",csrf.getToken());
        }
    }
}
