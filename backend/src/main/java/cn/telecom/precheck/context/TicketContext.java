package cn.telecom.precheck.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/** 金额采用十进制字符串传输；每次读取保留来源与时间，后续用于快照。 */
public record TicketContext(
        String schemaVersion, String ticketId, String customerRef, String complaint,
        boolean synthetic, String sourceSystem, Instant capturedAt,
        EvidenceSet<Bill> bills, EvidenceSet<Subscription> subscriptions,
        EvidenceSet<Authorization> authorizations) {

    public enum RetrievalStatus { AVAILABLE, NOT_FOUND, UNAVAILABLE }

    /** NOT_FOUND 表示查询成功但未找到，UNAVAILABLE 表示无法查询，均不等于违规。 */
    public record EvidenceSet<T>(RetrievalStatus status, List<T> items) {
        public EvidenceSet {
            items = List.copyOf(items);
            if (status == null
                    || (status == RetrievalStatus.AVAILABLE && items.isEmpty())
                    || (status != RetrievalStatus.AVAILABLE && !items.isEmpty())) {
                throw new IllegalArgumentException("证据状态与内容不一致");
            }
        }
    }

    public record Bill(String evidenceId, String serviceId, String subscriptionId,
                       YearMonth billingPeriod,
                       @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
                       BigDecimal amount, String currency) {}

    public record Subscription(String evidenceId, String subscriptionId, String serviceId,
                               String serviceName,
                               @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
                               BigDecimal monthlyPrice, String currency, Instant orderedAt) {}

    public record Authorization(String evidenceId, String subscriptionId,
                                String channel, Instant authorizedAt, String description) {}
}
