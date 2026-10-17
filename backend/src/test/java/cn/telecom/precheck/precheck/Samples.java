package cn.telecom.precheck.precheck;

final class Samples {
    static final String VALID = """
        {"complaintType":"VALUE_ADDED_CHARGE_DISPUTE","disputedAmount":30.00,"currency":"CNY",
         "summary":"客户对30元视频会员费提出异议，希望核实订购情况。",
         "evidenceIds":["BILL-001","ORDER-001","AUTH-001"],"recommendation":"MANUAL_REVIEW"}
        """;
    static String forTicket(String suffix) { return VALID.replace("-001", "-" + suffix); }
}
