package cn.telecom.precheck.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import cn.telecom.precheck.context.TicketContext.RetrievalStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MySqlTicketContextProviderTest {
    @Test
    void loadsComplaintAndEvidenceOnlyFromDatabaseMapper() {
        var mapper = mock(HostTicketMapper.class);
        var now = Instant.parse("2026-10-19T05:00:00Z");
        when(mapper.find("TKT-1")).thenReturn(new HostTicketMapper.HostTicket(
                "TKT-1", "扣费争议", "摘要", "关注", "DRAFT", null, null, now,
                "CUSTOMER-1", "客户投诉原文", "增值业务扣费争议", "10000号热线", true,
                "mysql-host", "operator", "operator", now));
        when(mapper.bills("TKT-1")).thenReturn(List.of(new HostTicketMapper.BillRow(
                "BILL-1", "VIDEO", "SUB-1", "2026-10", new BigDecimal("30.00"), "CNY")));
        when(mapper.subscriptions("TKT-1")).thenReturn(List.of());
        when(mapper.authorizations("TKT-1")).thenReturn(List.of());

        var result = new MySqlTicketContextProvider(mapper).getContext("TKT-1");

        assertThat(result.customerRef()).isEqualTo("CUSTOMER-1");
        assertThat(result.complaint()).isEqualTo("客户投诉原文");
        assertThat(result.bills().status()).isEqualTo(RetrievalStatus.AVAILABLE);
        assertThat(result.subscriptions().status()).isEqualTo(RetrievalStatus.NOT_FOUND);
        assertThat(result.authorizations().status()).isEqualTo(RetrievalStatus.NOT_FOUND);
        assertThat(result.bills().items().get(0).amount()).isEqualByComparingTo("30.00");
    }
}
