package cn.telecom.precheck;

import cn.telecom.precheck.adapter.MockTicketContextProvider;
import cn.telecom.precheck.context.TicketContext.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MockContextTest {
    private final MockTicketContextProvider provider = new MockTicketContextProvider();
    @Test void evidenceReferencesTheSameSubscriptionAndIdsAreDistinct() {
        var context = provider.getContext("DEMO-001");
        var bill = context.bills().items().get(0);
        var order = context.subscriptions().items().get(0);
        var auth = context.authorizations().items().get(0);
        assertThat(bill.subscriptionId()).isEqualTo(order.subscriptionId()).isEqualTo(auth.subscriptionId());
        assertThat(bill.serviceId()).isEqualTo(order.serviceId());
        assertThat(List.of(bill.evidenceId(), order.evidenceId(), auth.evidenceId())).doesNotHaveDuplicates();
    }
    @Test void missingAuthorizationDoesNotInventEvidence() {
        var context = provider.getContext("DEMO-002");
        assertThat(context.authorizations().status()).isEqualTo(RetrievalStatus.NOT_FOUND);
        assertThat(context.authorizations().items()).isEmpty();
    }
    @Test void inconsistentAmountsArePreservedForLaterRuleChecking() {
        var context = provider.getContext("DEMO-003");
        assertThat(context.bills().items().get(0).amount())
                .isNotEqualByComparingTo(context.subscriptions().items().get(0).monthlyPrice());
    }
    @Test void retrievalStatusCannotContradictEvidenceAndCopiesInput() {
        assertThatThrownBy(() -> new EvidenceSet<>(RetrievalStatus.AVAILABLE, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EvidenceSet<>(RetrievalStatus.NOT_FOUND, List.of("invented")))
                .isInstanceOf(IllegalArgumentException.class);
        var items = new ArrayList<>(List.of("evidence"));
        var set = new EvidenceSet<>(RetrievalStatus.AVAILABLE, items);
        items.clear();
        assertThat(set.items()).containsExactly("evidence");
        assertThatThrownBy(() -> set.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new EvidenceSet<>(RetrievalStatus.UNAVAILABLE, List.of()).status())
                .isNotEqualTo(RetrievalStatus.NOT_FOUND);
    }
}
