package com.serdar.user.config;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

/**
 * Builds an OpenSearch transport with optional basic auth.
 * Dev uses {@code http} without credentials; prod can use {@code https} with
 * a trusted certificate (default JVM trust store — no custom SSL hook needed).
 */
public final class OpenSearchTransportSupport {

    private OpenSearchTransportSupport() {}

    public static OpenSearchTransport build(
            String host,
            int port,
            String scheme,
            String username,
            String password) {
        HttpHost httpHost = new HttpHost(scheme, host, port);
        ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder.builder(httpHost);
        builder.setMapper(new JacksonJsonpMapper());
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(
                        new AuthScope(httpHost),
                        new UsernamePasswordCredentials(username, password.toCharArray()));
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
            }
            return httpClientBuilder;
        });
        return builder.build();
    }
}
