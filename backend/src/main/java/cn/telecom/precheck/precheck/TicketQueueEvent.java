package cn.telecom.precheck.precheck;

import jakarta.validation.constraints.*;

/** 宿主工单进入投诉处理队列的内部事件契约。 */
public record TicketQueueEvent(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String eventId,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String ticketId,
        @Positive long ticketVersion) {}
