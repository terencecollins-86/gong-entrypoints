package io.gong.gongentrypoints.datacapture;

import io.gong.gongentrypoints.DownstreamLoggingInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds one {@link RestClient} per gong-data-capture module. Each client logs downstream
 * calls (URL, target, payload) via a {@link DownstreamLoggingInterceptor}.
 */
@Configuration
@EnableConfigurationProperties(DataCaptureProperties.class)
public class DataCaptureClientConfig {

    @Bean("dcpApiServerClient")
    public RestClient dcpApiServerClient(DataCaptureProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.apiServerUrl())
                .requestInterceptor(new DownstreamLoggingInterceptor("dcp-api-server"))
                .build();
    }

    @Bean("dcpTasksClient")
    public RestClient dcpTasksClient(DataCaptureProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.tasksUrl())
                .requestInterceptor(new DownstreamLoggingInterceptor("dcp-tasks"))
                .build();
    }
}
