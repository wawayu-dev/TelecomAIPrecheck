package cn.telecom.precheck.precheck;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("mysql")
@EnableConfigurationProperties(XfyunProperties.class)
public class PrecheckConfiguration {
    @Bean(destroyMethod="close")
    public XfyunPrecheckModel precheckModel(XfyunProperties properties) { return new XfyunPrecheckModel(properties); }
}
