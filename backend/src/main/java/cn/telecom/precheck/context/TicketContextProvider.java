package cn.telecom.precheck.context;

/** 宿主只读边界：实现方负责访问权限、数据最小化和证据关联，不暴露宿主表结构。 */
public interface TicketContextProvider {
    TicketContext getContext(String ticketId);
}
