package com.tudou.tudoumianshi.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
@Slf4j
@Data
public class PulsarConfig {

    @Value("${pulsar.service-url:pulsar://localhost:6650}")
    private String serviceUrl;

    @Value("${pulsar.io-threads:1}")
    private int ioThreads;

    @Value("${pulsar.listener-threads:1}")
    private int listenerThreads;

    @Value("${pulsar.enable-tcp-no-delay:true}")
    private boolean enableTcpNoDelay;

    private PulsarClient pulsarClient;

    @Bean
    public PulsarClient pulsarClient() throws PulsarClientException {
        log.info("初始化PulsarClient: serviceUrl={}", serviceUrl);
        
        pulsarClient = PulsarClient.builder()
                .serviceUrl(serviceUrl)
                .ioThreads(ioThreads)
                .listenerThreads(listenerThreads)
                .enableTcpNoDelay(enableTcpNoDelay)
                .build();
                
        return pulsarClient;
    }
    
    @PreDestroy
    public void destroy() {
        if (pulsarClient != null) {
            try {
                log.info("关闭PulsarClient");
                pulsarClient.close();
            } catch (PulsarClientException e) {
                log.error("关闭PulsarClient失败", e);
            }
        }
    }
} 