package io.gong.gongentrypoints.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used by triggers to call the Telephony Systems troubleshooters.
 */
@Configuration
@EnableConfigurationProperties(TelephonyProperties.class)
public class TelephonyClientConfig {

    @Bean
    public RestClient telephonyRestClient(TelephonyProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build();
    }
}
