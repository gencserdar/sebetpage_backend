package com.serdar.user.config;



import org.opensearch.client.opensearch.OpenSearchClient;

import org.opensearch.client.transport.OpenSearchTransport;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;



@Configuration

public class OpenSearchConfig {



    @Bean(destroyMethod = "close")

    OpenSearchTransport openSearchTransport(

            @Value("${app.opensearch.host}") String host,

            @Value("${app.opensearch.port}") int port,

            @Value("${app.opensearch.scheme}") String scheme,

            @Value("${app.opensearch.username}") String username,

            @Value("${app.opensearch.password}") String password) {

        return OpenSearchTransportSupport.build(host, port, scheme, username, password);

    }



    @Bean

    OpenSearchClient openSearchClient(OpenSearchTransport transport) {

        return new OpenSearchClient(transport);

    }

}

