package cn.telecom.precheck.precheck;

import cn.telecom.precheck.adapter.MockTicketContextProvider;
import cn.telecom.precheck.context.TicketContext;
import cn.telecom.precheck.context.TicketContext.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static cn.telecom.precheck.precheck.EvidenceVerifier.Code.*;

class EvidenceVerifierTest {
    final MockTicketContextProvider provider = new MockTicketContextProvider();
    final EvidenceVerifier verifier = new EvidenceVerifier();
    @Test void completeEvidenceHasNoMissingFindings() {
        var report = verifier.verify(provider.getContext("DEMO-001"));
        assertThat(report.findings()).isEmpty();
        assertThat(report.authorizationIncomplete()).isTrue();
        assertThat(report.evidenceIds()).containsExactlyInAnyOrder("BILL-001","ORDER-001","AUTH-001");
        assertThat(report.verifiedFact()).contains("30.00 元", "未发现计价差异");
    }
    @Test void missingAuthorizationIsExplicit() {
        var report = verifier.verify(provider.getContext("DEMO-002"));
        assertThat(report.findings()).extracting(EvidenceVerifier.Finding::code).contains(AUTHORIZATION_MISSING);
        assertThat(report.authorizationIncomplete()).isTrue();
    }
    @Test void differingAmountsReferenceBothSourceRecords() {
        var report = verifier.verify(provider.getContext("DEMO-003"));
        assertThat(report.findings()).contains(new EvidenceVerifier.Finding(AMOUNT_MISMATCH,List.of("BILL-003","ORDER-003")));
    }
    @Test void failedAuthorizationQueryIsNotReportedAsMissing() {
        var c = provider.getContext("DEMO-001");
        var report = verifier.verify(with(c,c.bills(),new EvidenceSet<>(RetrievalStatus.UNAVAILABLE,List.of())));
        assertThat(report.findings()).extracting(EvidenceVerifier.Finding::code)
                .contains(AUTHORIZATION_UNAVAILABLE).doesNotContain(AUTHORIZATION_MISSING);
    }
    @Test void unrelatedAuthorizationDoesNotAuthorizeBill() {
        var c=provider.getContext("DEMO-001");
        var a=c.authorizations().items().get(0);
        var report=verifier.verify(with(c,c.bills(),new EvidenceSet<>(RetrievalStatus.AVAILABLE,
                List.of(new Authorization(a.evidenceId(),"OTHER",a.channel(),a.authorizedAt(),a.description())))));
        assertThat(report.authorizationIncomplete()).isTrue();
        assertThat(report.findings()).extracting(EvidenceVerifier.Finding::code).contains(AUTHORIZATION_NOT_LINKED);
    }
    @Test void duplicateEvidenceIdsAreRejectedBeforeModelCall() {
        var c=provider.getContext("DEMO-001"); var b=c.bills().items().get(0);
        var changed = new Bill("ORDER-001",b.serviceId(),b.subscriptionId(),b.billingPeriod(),b.amount(),b.currency());
        assertThatThrownBy(() -> verifier.verify(with(c,new EvidenceSet<>(RetrievalStatus.AVAILABLE,List.of(changed)),c.authorizations())))
                .isInstanceOf(PrecheckFailure.class).hasMessage("INVALID_CONTEXT");
    }
    @Test void billWithUnmatchedSubscriptionIsFlagged() {
        var c=provider.getContext("DEMO-001");var b=c.bills().items().get(0);
        var changed=new Bill(b.evidenceId(),b.serviceId(),"OTHER",b.billingPeriod(),b.amount(),b.currency());
        var report=verifier.verify(with(c,new EvidenceSet<>(RetrievalStatus.AVAILABLE,List.of(changed)),c.authorizations()));
        assertThat(report.findings()).extracting(EvidenceVerifier.Finding::code).contains(ORDER_NOT_LINKED);
        assertThat(report.authorizationIncomplete()).isTrue();
    }
    private TicketContext with(TicketContext c,EvidenceSet<Bill> bills,EvidenceSet<Authorization> auth) {
        return new TicketContext(c.schemaVersion(),c.ticketId(),c.customerRef(),c.complaint(),c.synthetic(),
                c.sourceSystem(),c.capturedAt(),bills,c.subscriptions(),auth);
    }
}
