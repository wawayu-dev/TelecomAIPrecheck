package cn.telecom.precheck.adapter;

import cn.telecom.precheck.context.TicketContextProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ContextProviderConfiguration {
    @Bean
    @ConditionalOnProperty(name = "precheck.context-provider", havingValue = "mock")
    @ConditionalOnMissingBean(TicketContextProvider.class)
    TicketContextProvider mockTicketContextProvider() {
        return new MockTicketContextProvider();
    }
}
