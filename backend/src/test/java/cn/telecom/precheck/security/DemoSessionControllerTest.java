package cn.telecom.precheck.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class DemoSessionControllerTest {
    private final SecurityConfiguration.DemoSessionController controller =
        new SecurityConfiguration.DemoSessionController();

    @Test void allowlistedRoleCreatesServerSessionWithoutPassword() {
        var request = new MockHttpServletRequest();
        var result = controller.login(Map.of("account", "reviewer"),
            new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"), request, new MockHttpServletResponse());
        assertThat(result).containsEntry("actorId", "reviewer");
        assertThat(result.get("roles")).isEqualTo(java.util.List.of("ROLE_REVIEWER"));
        var context = (SecurityContext) request.getSession().getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context.getAuthentication().getName()).isEqualTo("reviewer");
    }

    @Test void arbitraryRoleIsRejected() {
        assertThatThrownBy(() -> controller.login(Map.of("account", "admin"),
            new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token"),
            new MockHttpServletRequest(), new MockHttpServletResponse()))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
