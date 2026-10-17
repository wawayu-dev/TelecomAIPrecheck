package cn.telecom.precheck.web;

import cn.telecom.precheck.context.TicketContext;
import cn.telecom.precheck.context.TicketContextProvider;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class TicketContextController {
    private final TicketContextProvider provider;
    public TicketContextController(TicketContextProvider provider) { this.provider = provider; }

    @GetMapping("/{ticketId}/context")
    public TicketContext context(@PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String ticketId) {
        return provider.getContext(ticketId);
    }
}
