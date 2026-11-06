package com.example.k3sdemo.api;

import java.time.Instant;

public record PodInfo(
        String service,
        String version,
        String podName,
        String podIp,
        String namespace,
        String nodeName,
        String javaVersion,
        Instant startedAt
) {
}
