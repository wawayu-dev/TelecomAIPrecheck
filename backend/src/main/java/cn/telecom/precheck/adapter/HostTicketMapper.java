package cn.telecom.precheck.adapter;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface HostTicketMapper {
    record HostTicket(String ticketId, String title, String note, String priority,
                      String status, String eventId, Long ticketVersion, Instant updatedAt,
                      String customerRef, String complaint, String category, String channel,
                      boolean synthetic, String sourceSystem, String createdBy, String updatedBy,
                      Instant createdAt) {}
    record BillRow(String evidenceId, String serviceId, String subscriptionId,
                   String billingPeriod, BigDecimal amount, String currency) {}
    record SubscriptionRow(String evidenceId, String subscriptionId, String serviceId,
                           String serviceName, BigDecimal monthlyPrice, String currency, Instant orderedAt) {}
    record AuthorizationRow(String evidenceId, String subscriptionId, String channel,
                            Instant authorizedAt, String description) {}

    String COLUMNS = "ticket_id AS ticketId,title,note,priority,status,event_id AS eventId,"
            + "ticket_version AS ticketVersion,updated_at AS updatedAt,customer_ref AS customerRef,"
            + "complaint,category,channel,synthetic,source_system AS sourceSystem,created_by AS createdBy,"
            + "updated_by AS updatedBy,created_at AS createdAt";

    @Select("SELECT " + COLUMNS + " FROM host_complaint_ticket ORDER BY ticket_id")
    List<HostTicket> list();

    @Select("SELECT " + COLUMNS + " FROM host_complaint_ticket WHERE ticket_id=#{ticketId}")
    HostTicket find(String ticketId);

    @Insert("""
        INSERT INTO host_complaint_ticket
        (ticket_id,title,note,priority,status,customer_ref,complaint,category,channel,synthetic,
         source_system,created_by,updated_by,created_at,updated_at)
        VALUES(#{ticketId},#{title},#{note},#{priority},'DRAFT',#{customerRef},#{complaint},
         #{category},#{channel},TRUE,'mysql-host',#{actor},#{actor},UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
        """)
    int insertDraft(@Param("ticketId") String ticketId, @Param("title") String title,
            @Param("note") String note, @Param("priority") String priority,
            @Param("customerRef") String customerRef, @Param("complaint") String complaint,
            @Param("category") String category, @Param("channel") String channel,
            @Param("actor") String actor);

    @Update("""
        UPDATE host_complaint_ticket SET title=#{title},note=#{note},priority=#{priority},
          customer_ref=#{customerRef},complaint=#{complaint},category=#{category},channel=#{channel},
          updated_by=#{actor},updated_at=UTC_TIMESTAMP(6)
        WHERE ticket_id=#{ticketId} AND status='DRAFT'
        """)
    int updateDraft(@Param("ticketId") String ticketId, @Param("title") String title,
            @Param("note") String note, @Param("priority") String priority,
            @Param("customerRef") String customerRef, @Param("complaint") String complaint,
            @Param("category") String category, @Param("channel") String channel,
            @Param("actor") String actor);

    @Update("""
        UPDATE host_complaint_ticket
        SET status='SUBMITTED',event_id=#{eventId},ticket_version=#{ticketVersion},
          updated_by=#{actor},updated_at=UTC_TIMESTAMP(6)
        WHERE ticket_id=#{ticketId} AND status IN ('DRAFT','NEEDS_SUPPLEMENT')
        """)
    int submit(@Param("ticketId") String ticketId, @Param("eventId") String eventId,
               @Param("ticketVersion") long ticketVersion, @Param("actor") String actor);

    @Update("""
        UPDATE host_complaint_ticket
        SET status=#{status},updated_by=#{actor},updated_at=UTC_TIMESTAMP(6)
        WHERE ticket_id=#{ticketId}
        """)
    int updateStatus(@Param("ticketId") String ticketId, @Param("status") String status,
                     @Param("actor") String actor);

    @Select("""
        SELECT evidence_id AS evidenceId,service_id AS serviceId,subscription_id AS subscriptionId,
          billing_period AS billingPeriod,amount,currency
        FROM host_bill_evidence WHERE ticket_id=#{ticketId} ORDER BY evidence_id
        """)
    List<BillRow> bills(String ticketId);

    @Select("""
        SELECT evidence_id AS evidenceId,subscription_id AS subscriptionId,service_id AS serviceId,
          service_name AS serviceName,monthly_price AS monthlyPrice,currency,ordered_at AS orderedAt
        FROM host_subscription_evidence WHERE ticket_id=#{ticketId} ORDER BY evidence_id
        """)
    List<SubscriptionRow> subscriptions(String ticketId);

    @Select("""
        SELECT evidence_id AS evidenceId,subscription_id AS subscriptionId,channel,
          authorized_at AS authorizedAt,description
        FROM host_authorization_evidence WHERE ticket_id=#{ticketId} ORDER BY evidence_id
        """)
    List<AuthorizationRow> authorizations(String ticketId);
}
