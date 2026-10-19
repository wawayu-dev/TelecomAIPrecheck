package cn.telecom.precheck.adapter;

import cn.telecom.precheck.context.TicketContext;
import cn.telecom.precheck.context.TicketContextProvider;
import cn.telecom.precheck.context.TicketNotFoundException;
import java.time.YearMonth;
import java.util.List;
import static cn.telecom.precheck.context.TicketContext.*;

/** 演示宿主的数据库实现；真实集成可用同一接口替换为 CRM/计费系统 API。 */
public final class MySqlTicketContextProvider implements TicketContextProvider {
    private final HostTicketMapper tickets;
    public MySqlTicketContextProvider(HostTicketMapper tickets) { this.tickets = tickets; }

    @Override
    public TicketContext getContext(String ticketId) {
        var ticket = tickets.find(ticketId);
        if (ticket == null) throw new TicketNotFoundException();
        var bills = tickets.bills(ticketId).stream().map(row -> new Bill(row.evidenceId(), row.serviceId(),
                row.subscriptionId(), YearMonth.parse(row.billingPeriod()), row.amount(), row.currency())).toList();
        var subscriptions = tickets.subscriptions(ticketId).stream().map(row -> new Subscription(row.evidenceId(),
                row.subscriptionId(), row.serviceId(), row.serviceName(), row.monthlyPrice(), row.currency(),
                row.orderedAt())).toList();
        var authorizations = tickets.authorizations(ticketId).stream().map(row -> new Authorization(row.evidenceId(),
                row.subscriptionId(), row.channel(), row.authorizedAt(), row.description())).toList();
        return new TicketContext("1.0", ticket.ticketId(), ticket.customerRef(), ticket.complaint(),
                ticket.synthetic(), ticket.sourceSystem(), ticket.updatedAt(), evidence(bills),
                evidence(subscriptions), evidence(authorizations));
    }

    private static <T> EvidenceSet<T> evidence(List<T> rows) {
        return rows.isEmpty() ? new EvidenceSet<>(RetrievalStatus.NOT_FOUND, List.of())
                : new EvidenceSet<>(RetrievalStatus.AVAILABLE, rows);
    }
}
