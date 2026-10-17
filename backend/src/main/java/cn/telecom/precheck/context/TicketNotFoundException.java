package cn.telecom.precheck.context;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException() { super("未找到工单"); }
}
