package com.example.k3sdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application and pod metadata.
 *
 * <p>The Kubernetes Deployment supplies these values through environment
 * variables and the Downward API. Outside Kubernetes, application.yml provides
 * local defaults.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String name,
        String version,
        String greeting,
        String podName,
        String podIp,
        String podNamespace,
        String nodeName
) {
}
