package cn.telecom.precheck.precheck;

final class Samples {
    static final String VALID = """
        {"complaintType":"VALUE_ADDED_CHARGE_DISPUTE","disputedAmount":30.00,"currency":"CNY",
         "summary":"账单金额与订购价格一致，未发现价格异常。",
         "evidenceGap":"存在授权记录，但尚未确认授权过程能否证明客户主动订购。",
         "suggestedAction":"核实授权时间、验证方式和操作主体，并关联订购流水。",
         "evidenceIds":["BILL-001","ORDER-001","AUTH-001"],"recommendation":"MANUAL_REVIEW"}
        """;
    static String forTicket(String suffix) { return VALID.replace("-001", "-" + suffix); }
}
