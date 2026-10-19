package cn.telecom.precheck.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import cn.telecom.precheck.precheck.AsyncPrecheckService;
import cn.telecom.precheck.precheck.PrecheckMapper;
import cn.telecom.precheck.precheck.PrecheckRun;
import cn.telecom.precheck.review.ReviewMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class MockHostTransitionControllerTest {
    @Test
    void hostTransitionRemainsSuccessfulWhenPrecheckDeliveryFails() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))));
        var prechecks = mock(AsyncPrecheckService.class);
        var hostTickets = mock(HostTicketMapper.class);
        when(hostTickets.find("DEMO-001")).thenReturn(ticket("DRAFT", now()));
        when(hostTickets.submit("DEMO-001", "EV-1", 1L, "operator")).thenReturn(1);
        doThrow(new IllegalStateException("delivery unavailable")).when(prechecks).accept(any(), any());
        var controller = new MockHostTransitionController(prechecks, hostTickets,
                mock(PrecheckMapper.class), mock(ReviewMapper.class));

        var result = controller.transition("DEMO-001",
                new MockHostTransitionController.TransitionRequest("EV-1", 1L), mock(HttpServletRequest.class));

        assertThat(result).containsEntry("transitioned", true).containsEntry("precheckAccepted", false);
        verify(hostTickets).submit("DEMO-001", "EV-1", 1L, "operator");
        SecurityContextHolder.clearContext();
    }

    @Test
    void latestPendingRunOverridesOlderCompletedHostState() {
        var hostTickets = mock(HostTicketMapper.class);
        var runs = mock(PrecheckMapper.class);
        var reviews = mock(ReviewMapper.class);
        var now = Instant.parse("2026-10-19T05:00:00Z");
        when(hostTickets.list()).thenReturn(List.of(ticket("COMPLETED", now)));
        when(runs.findCurrent("DEMO-001")).thenReturn(new PrecheckRun(
                "00000000-0000-0000-0000-000000000001", "DEMO-001", "PENDING_REVIEW", "SUCCESS",
                "v1", "model", "model", "trace", now, now, 1L, null, null, null, 1,
                "mock-host", "EV-new", 2L, "EVENT", "COMPLETED", 1, 3, true, now, null, now));

        var controller = new MockHostTransitionController(mock(AsyncPrecheckService.class), hostTickets, runs, reviews);
        var summary = controller.tickets().get(0);

        assertThat(summary.workflowStatus()).isEqualTo("SUBMITTED");
        assertThat(summary.statusLabel()).isEqualTo("待核查");
        assertThat(summary.reviewerBucket()).isEqualTo("PENDING");
    }

    private static Instant now() { return Instant.parse("2026-10-19T05:00:00Z"); }
    private static HostTicketMapper.HostTicket ticket(String status, Instant now) {
        return new HostTicketMapper.HostTicket("DEMO-001", "视频会员扣费争议", "账单与授权材料齐全",
                "普通", status, "EV-old", 1L, now, "SYNTHETIC-CUSTOMER-001",
                "这个月扣了30元视频会员费", "增值业务扣费争议", "10000号热线", true,
                "mysql-host", "operator", "operator", now);
    }
}
