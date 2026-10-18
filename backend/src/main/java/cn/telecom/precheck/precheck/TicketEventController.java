package cn.telecom.precheck.precheck;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mysql")
@RequestMapping("/api/internal/ticket-events")
public class TicketEventController {
    private final AsyncPrecheckService service;
    public TicketEventController(AsyncPrecheckService service) { this.service = service; }

    @PostMapping("/complaint-queue")
    @PreAuthorize("hasRole('HOST')")
    public ResponseEntity<AsyncPrecheckService.Receipt> receive(@Valid @RequestBody TicketQueueEvent event,
                                                                HttpServletRequest request) {
        var receipt = service.accept(event, (String) request.getAttribute("traceId"));
        return ResponseEntity.status(receipt.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(receipt);
    }
}
