package cn.telecom.precheck.precheck;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("precheck.events")
public record TicketEventProperties(String source) {
    public TicketEventProperties {
        if (source == null || !source.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")) source = "mock-host";
    }
}
