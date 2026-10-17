package cn.telecom.precheck.precheck;

import cn.telecom.precheck.context.TicketContext;
import cn.telecom.precheck.context.TicketContext.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static cn.telecom.precheck.precheck.PrecheckFailure.Code.INVALID_CONTEXT;

/** 简单月费演示规则，不等同于真实运营商计费或授权有效性认定。 */
@Component
public class EvidenceVerifier {
    public enum Code { BILL_MISSING, BILL_UNAVAILABLE, ORDER_MISSING, ORDER_UNAVAILABLE,
        AUTHORIZATION_MISSING, AUTHORIZATION_UNAVAILABLE, ORDER_NOT_LINKED,
        AUTHORIZATION_NOT_LINKED, SERVICE_MISMATCH, AMOUNT_MISMATCH }
    public record Finding(Code code, List<String> evidenceIds) {}
    public record Report(List<Finding> findings, Set<String> evidenceIds, boolean authorizationIncomplete) {}

    public Report verify(TicketContext context) {
        try { return inspect(context); }
        catch (PrecheckFailure failure) { throw failure; }
        catch (RuntimeException invalid) { throw new PrecheckFailure(INVALID_CONTEXT); }
    }
    private Report inspect(TicketContext c) {
        if (c == null || !"1.0".equals(c.schemaVersion()) || c.complaint() == null
                || c.complaint().isBlank() || c.complaint().length() > 10000
                || c.ticketId() == null || c.capturedAt() == null) fail();
        Set<String> ids = new HashSet<>();
        List<Finding> findings = new ArrayList<>();
        status(c.bills(), Code.BILL_MISSING, Code.BILL_UNAVAILABLE, findings);
        status(c.subscriptions(), Code.ORDER_MISSING, Code.ORDER_UNAVAILABLE, findings);
        status(c.authorizations(), Code.AUTHORIZATION_MISSING, Code.AUTHORIZATION_UNAVAILABLE, findings);
        Map<String, Subscription> orders = new HashMap<>();
        for (var order : c.subscriptions().items()) {
            addId(ids, order.evidenceId());
            required(order.subscriptionId()); required(order.serviceId()); required(order.serviceName());
            if (order.monthlyPrice() == null || order.monthlyPrice().signum() < 0
                    || !"CNY".equals(order.currency()) || order.orderedAt() == null) fail();
            if (orders.put(order.subscriptionId(), order) != null) fail();
        }
        for (var auth : c.authorizations().items()) {
            addId(ids, auth.evidenceId()); required(auth.subscriptionId()); required(auth.channel());
            required(auth.description());
            if (auth.authorizedAt() == null) fail();
            if (!orders.containsKey(auth.subscriptionId())) add(findings, Code.AUTHORIZATION_NOT_LINKED, auth.evidenceId());
        }
        boolean incomplete = c.authorizations().status() != RetrievalStatus.AVAILABLE
                || c.bills().status() != RetrievalStatus.AVAILABLE;
        for (var bill : c.bills().items()) {
            addId(ids, bill.evidenceId()); required(bill.subscriptionId()); required(bill.serviceId());
            if (bill.amount() == null || bill.amount().signum() < 0 || bill.billingPeriod() == null
                    || !"CNY".equals(bill.currency())) fail();
            var order = orders.get(bill.subscriptionId());
            if (order == null) { add(findings, Code.ORDER_NOT_LINKED, bill.evidenceId()); incomplete = true; }
            else {
                if (!bill.serviceId().equals(order.serviceId())) {
                    add(findings, Code.SERVICE_MISMATCH, bill.evidenceId(), order.evidenceId()); incomplete = true;
                }
                if (bill.amount().compareTo(order.monthlyPrice()) != 0) add(findings, Code.AMOUNT_MISMATCH, bill.evidenceId(), order.evidenceId());
            }
            if (c.authorizations().items().stream().noneMatch(a -> a.subscriptionId().equals(bill.subscriptionId()))) {
                incomplete = true;
                if (c.authorizations().status() == RetrievalStatus.AVAILABLE) add(findings, Code.AUTHORIZATION_NOT_LINKED, bill.evidenceId());
            }
        }
        if (ids.size() > 100) fail();
        return new Report(List.copyOf(findings), Set.copyOf(ids), incomplete);
    }
    private void status(EvidenceSet<?> set, Code missing, Code unavailable, List<Finding> findings) {
        if (set.status() == RetrievalStatus.NOT_FOUND) add(findings, missing);
        if (set.status() == RetrievalStatus.UNAVAILABLE) add(findings, unavailable);
    }
    private void addId(Set<String> ids, String id) {
        required(id);
        if (!id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(id)) fail();
    }
    private void required(String value) { if (value == null || value.isBlank() || value.length() > 1000) fail(); }
    private void add(List<Finding> findings, Code code, String... ids) { findings.add(new Finding(code, List.of(ids))); }
    private void fail() { throw new PrecheckFailure(INVALID_CONTEXT); }
}
