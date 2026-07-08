package io.gong.gongentrypoints.telephonysystems;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link RestClient} used by Telephony Systems triggers to call the troubleshooters.
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
