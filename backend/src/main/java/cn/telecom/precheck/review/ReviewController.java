package cn.telecom.precheck.review;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("mysql") @RequestMapping("/api/ai-prechecks/{id}")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service=service; }
    @PostMapping("/review")
    public Review submit(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id,
                         @Valid @RequestBody ReviewRequest body,HttpServletRequest request) {
        return service.submit(id,body,(String)request.getAttribute("traceId"));
    }
    @GetMapping("/review")
    public Review get(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id) { return service.get(id); }
    @GetMapping("/audit")
    public List<AuditEvent> audit(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id,
            @RequestParam(defaultValue="0") @Min(0) long after,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) { return service.audit(id,after,limit); }
    @GetMapping("/timeline")
    public List<ReviewService.TimelineItem> timeline(@PathVariable @Pattern(regexp="[a-f0-9-]{36}") String id,
            @RequestParam(defaultValue="0") @Min(0) long after,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) { return service.timeline(id,after,limit); }
}
