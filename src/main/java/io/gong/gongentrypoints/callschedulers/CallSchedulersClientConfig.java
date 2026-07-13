package io.gong.gongentrypoints.callschedulers;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CallSchedulersProperties.class)
public class CallSchedulersClientConfig {

    @Bean
    public CallSchedulersTarget callSchedulersTarget(CallSchedulersProperties properties) {
        return new CallSchedulersTarget(properties);
    }
}
