package cn.telecom.precheck.adapter;

import cn.telecom.precheck.context.TicketContextProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
public class ContextProviderConfiguration {
    @Bean
    @ConditionalOnProperty(name = "precheck.context-provider", havingValue = "mock")
    @ConditionalOnMissingBean(TicketContextProvider.class)
    TicketContextProvider mockTicketContextProvider() {
        return new MockTicketContextProvider();
    }

    @Bean
    @Profile("mysql")
    @ConditionalOnProperty(name = "precheck.context-provider", havingValue = "mysql")
    @ConditionalOnMissingBean(TicketContextProvider.class)
    TicketContextProvider mysqlTicketContextProvider(HostTicketMapper tickets) {
        return new MySqlTicketContextProvider(tickets);
    }
}
