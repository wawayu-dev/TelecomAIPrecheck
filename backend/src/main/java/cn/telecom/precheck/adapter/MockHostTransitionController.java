package cn.telecom.precheck.adapter;

import cn.telecom.precheck.precheck.AsyncPrecheckService;
import cn.telecom.precheck.precheck.PrecheckMapper;
import cn.telecom.precheck.precheck.TicketQueueEvent;
import cn.telecom.precheck.context.TicketNotFoundException;
import cn.telecom.precheck.review.ReviewMapper;
import cn.telecom.precheck.security.CurrentActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
    public record TicketDraftRequest(
            @NotBlank @Size(max=128) String title,
            @NotBlank @Size(max=64) String customerRef,
            @NotBlank @Size(max=64) String category,
            @NotBlank @Size(max=1000) String complaint,
            @NotBlank @Size(max=32) String channel,
            @NotBlank @Pattern(regexp="普通|关注|紧急") String priority) {}
    public record TicketSummary(String id, String title, String note, String priority,
                                String workflowStatus, String statusLabel, String reviewerBucket,
                                boolean submitted, Instant updatedAt, String category, String channel) {}
    private final AsyncPrecheckService prechecks;
    private final HostTicketMapper tickets;
    private final PrecheckMapper runs;
    private final ReviewMapper reviews;
    public MockHostTransitionController(AsyncPrecheckService prechecks, HostTicketMapper tickets,
            PrecheckMapper runs, ReviewMapper reviews) {
        this.prechecks = prechecks; this.tickets = tickets; this.runs = runs; this.reviews = reviews;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','REVIEWER','AUDITOR')")
    public List<TicketSummary> tickets() {
        return tickets.list().stream().map(this::summary).toList();
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERATOR')")
    public TicketSummary create(@Valid @RequestBody TicketDraftRequest body) {
        String id = "TKT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        if (tickets.insertDraft(id, body.title().trim(), note(body.complaint()), body.priority(),
                body.customerRef().trim(), body.complaint().trim(), body.category().trim(),
                body.channel().trim(), CurrentActor.id()) != 1) throw new IllegalStateException("Ticket insert failed");
        return summary(tickets.find(id));
    }

    @PutMapping("/{ticketId}")
    @PreAuthorize("hasRole('OPERATOR')")
    public TicketSummary update(
            @PathVariable @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String ticketId,
            @Valid @RequestBody TicketDraftRequest body) {
        if (tickets.find(ticketId) == null) throw new TicketNotFoundException();
        if (tickets.updateDraft(ticketId, body.title().trim(), note(body.complaint()), body.priority(),
                body.customerRef().trim(), body.complaint().trim(), body.category().trim(),
                body.channel().trim(), CurrentActor.id()) != 1) throw new HostTicketConflictException();
        return summary(tickets.find(ticketId));
    }

    @PostMapping("/{ticketId}/complaint-queue")
    @PreAuthorize("hasRole('OPERATOR')")
    public Map<String,Object> transition(
            @PathVariable @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String ticketId,
            @Valid @RequestBody TransitionRequest body, HttpServletRequest request) {
        // 宿主状态先持久化；后续事件接入失败只改变 accepted，不回滚宿主流转。
        if (tickets.find(ticketId) == null) throw new TicketNotFoundException();
        if (tickets.submit(ticketId, body.eventId(), body.ticketVersion(), CurrentActor.id()) != 1)
            throw new HostTicketConflictException();
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

    private TicketSummary summary(HostTicketMapper.HostTicket ticket) {
        var run = runs.findCurrent(ticket.ticketId());
        String workflow = ticket.status(), label, bucket = null;
        if (run == null && "DRAFT".equals(workflow)) label = "登记中";
        else if (run == null && "COMPLETED".equals(workflow)) { label = "已完成"; bucket = "COMPLETED"; }
        else if (run == null && "NEEDS_SUPPLEMENT".equals(workflow)) { label = "待补充"; bucket = "PROCESSING"; }
        else if (run == null) { label = "待人工处理"; bucket = "PENDING"; }
        else if ("ANALYZING".equals(run.status())) {
            label = "核查中"; bucket = "PROCESSING"; workflow = "SUBMITTED";
        }
        else if ("REVIEWED".equals(run.status())) {
            var review = reviews.find(run.id());
            if (review != null && "CONFIRMED".equals(review.decision())) {
                label = "已完成"; bucket = "COMPLETED"; workflow = "COMPLETED";
            } else { label = "待补充"; bucket = "PROCESSING"; workflow = "NEEDS_SUPPLEMENT"; }
        } else { label = "待核查"; bucket = "PENDING"; workflow = "SUBMITTED"; }
        return new TicketSummary(ticket.ticketId(), ticket.title(), ticket.note(), ticket.priority(),
                workflow, label, bucket, !"DRAFT".equals(workflow), ticket.updatedAt(),
                ticket.category(), ticket.channel());
    }

    private static String note(String complaint) {
        String clean = complaint.trim().replaceAll("\\s+", " ");
        return clean.length() <= 40 ? clean : clean.substring(0, 40) + "…";
    }
}
