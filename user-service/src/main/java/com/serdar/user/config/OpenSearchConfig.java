package com.serdar.user.config;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean(destroyMethod = "close")
    OpenSearchTransport openSearchTransport(
            @Value("${app.opensearch.host}") String host,
            @Value("${app.opensearch.port}") int port,
            @Value("${app.opensearch.scheme}") String scheme) {
        HttpHost httpHost = new HttpHost(scheme, host, port);
        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(httpHost);
        builder.setMapper(new JacksonJsonpMapper());
        return builder.build();
    }

    @Bean
    OpenSearchClient openSearchClient(OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}
