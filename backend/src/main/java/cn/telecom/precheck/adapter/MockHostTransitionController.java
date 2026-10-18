package cn.telecom.precheck.adapter;

import cn.telecom.precheck.precheck.AsyncPrecheckService;
import cn.telecom.precheck.precheck.TicketQueueEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 仅用于演示宿主先完成工单流转、再尽力投递预处理事件的边界。 */
@RestController
@Profile("mysql")
@RequestMapping("/api/mock-host/tickets")
public class MockHostTransitionController {
    public record TransitionRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String eventId,
                                    @Positive long ticketVersion) {}
    private final AsyncPrecheckService prechecks;
    public MockHostTransitionController(AsyncPrecheckService prechecks) { this.prechecks = prechecks; }

    @PostMapping("/{ticketId}/complaint-queue")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String,Object> transition(
            @PathVariable @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String ticketId,
            @Valid @RequestBody TransitionRequest body, HttpServletRequest request) {
        // 这里代表宿主流转已经提交；后续事件接入失败只改变 accepted，不回滚流转。
        boolean accepted;
        String runId = null;
        try {
            var receipt = prechecks.accept(new TicketQueueEvent(body.eventId(), ticketId, body.ticketVersion()),
                    (String) request.getAttribute("traceId"));
            accepted = receipt.accepted();
            runId = receipt.runId();
        } catch (RuntimeException eventFailure) {
            accepted = false;
        }
        var result = new java.util.LinkedHashMap<String,Object>();
        result.put("ticketId", ticketId);
        result.put("ticketVersion", body.ticketVersion());
        result.put("transitioned", true);
        result.put("precheckAccepted", accepted);
        if (runId != null) result.put("precheckRunId", runId);
        return result;
    }
}
