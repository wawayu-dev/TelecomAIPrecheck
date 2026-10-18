package cn.telecom.precheck.precheck;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@Profile("mysql")
@EnableConfigurationProperties({PrecheckTaskProperties.class, TicketEventProperties.class})
public class AsyncPrecheckConfiguration {
    @Bean("precheckTaskExecutor")
    Executor precheckTaskExecutor(PrecheckTaskProperties properties) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.corePoolSize());
        executor.setMaxPoolSize(properties.maxPoolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setThreadNamePrefix("precheck-task-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
