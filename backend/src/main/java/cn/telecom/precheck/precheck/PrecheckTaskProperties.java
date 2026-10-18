package cn.telecom.precheck.precheck;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("precheck.tasks")
public record PrecheckTaskProperties(int corePoolSize, int maxPoolSize, int queueCapacity,
                                     int maxAttempts, int retryDelaySeconds) {
    public PrecheckTaskProperties {
        if (corePoolSize < 1) corePoolSize = 2;
        if (maxPoolSize < corePoolSize) maxPoolSize = Math.max(corePoolSize, 4);
        if (queueCapacity < 1) queueCapacity = 100;
        if (maxAttempts < 1) maxAttempts = 3;
        if (retryDelaySeconds < 0) retryDelaySeconds = 30;
    }
}
