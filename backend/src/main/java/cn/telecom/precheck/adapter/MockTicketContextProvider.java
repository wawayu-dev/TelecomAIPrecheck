package cn.telecom.precheck.adapter;

import cn.telecom.precheck.context.TicketContext;
import cn.telecom.precheck.context.TicketContextProvider;
import cn.telecom.precheck.context.TicketNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import static cn.telecom.precheck.context.TicketContext.*;

/** 所有内容均为合成数据，不代表真实客户、授权或收费认定。 */
public final class MockTicketContextProvider implements TicketContextProvider {
    @Override
    public TicketContext getContext(String ticketId) {
        if (!List.of("DEMO-001", "DEMO-002", "DEMO-003").contains(ticketId)) {
            throw new TicketNotFoundException();
        }
        String suffix = ticketId.substring(5);
        String subscriptionId = "SUB-" + suffix;
        var bill = new Bill("BILL-" + suffix, "VIDEO", subscriptionId,
                YearMonth.of(2026, 10), new BigDecimal("30.00"), "CNY");
        var subscription = new Subscription("ORDER-" + suffix, subscriptionId, "VIDEO", "视频会员",
                new BigDecimal(ticketId.equals("DEMO-003") ? "20.00" : "30.00"), "CNY",
                Instant.parse("2026-09-01T02:00:00Z"));
        EvidenceSet<Authorization> authorizations = ticketId.equals("DEMO-002")
                ? new EvidenceSet<>(RetrievalStatus.NOT_FOUND, List.of())
                : new EvidenceSet<>(RetrievalStatus.AVAILABLE, List.of(new Authorization(
                    "AUTH-" + suffix, subscriptionId, "MOCK_SMS",
                    Instant.parse("2026-09-01T02:00:00Z"), "合成短信确认记录，仅用于演示")));
        return new TicketContext("1.0", ticketId, "SYNTHETIC-CUSTOMER-" + suffix,
                "这个月扣了30元视频会员费，我不记得主动办理过，请帮我核实。", true,
                "mock-host", Instant.now(),
                new EvidenceSet<>(RetrievalStatus.AVAILABLE, List.of(bill)),
                new EvidenceSet<>(RetrievalStatus.AVAILABLE, List.of(subscription)), authorizations);
    }
}
