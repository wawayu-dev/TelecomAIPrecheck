package cn.telecom.precheck.precheck;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mysql")
@RequestMapping("/api/ai-prechecks")
public class PrecheckController {
    private final PrecheckService service;
    private final AsyncPrecheckService async;
    public PrecheckController(PrecheckService service, AsyncPrecheckService async) { this.service = service; this.async = async; }
    public record RetryRequest(@NotNull @Min(0) Integer expectedVersion) {}
    @PostMapping("/tickets/{ticketId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PrecheckService.View start(@PathVariable @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String ticketId,
                                      HttpServletRequest request) {
        return service.start(ticketId, (String) request.getAttribute("traceId"));
    }
    @GetMapping("/tickets/{ticketId}")
    public List<PrecheckService.View> history(@PathVariable @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String ticketId,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int offset) {
        return service.history(ticketId, limit, offset);
    }
    @GetMapping("/tickets/{ticketId}/current")
    public org.springframework.http.ResponseEntity<PrecheckService.View> current(
            @PathVariable @Pattern(regexp="[A-Za-z0-9_-]{1,64}") String ticketId) {
        var current = service.current(ticketId);
        return current == null ? org.springframework.http.ResponseEntity.noContent().build()
                : org.springframework.http.ResponseEntity.ok(current);
    }
    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AsyncPrecheckService.Receipt retry(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id,
            @Valid @RequestBody RetryRequest body, HttpServletRequest request) {
        return async.retry(id, body.expectedVersion(), (String) request.getAttribute("traceId"));
    }
    @GetMapping("/{id}")
    public PrecheckService.View get(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id) { return service.get(id); }
}
