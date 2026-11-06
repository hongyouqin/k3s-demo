package com.example.k3sdemo.api;

import java.time.Instant;

public record HelloResponse(
        String message,
        String podName,
        long requestNumber,
        Instant timestamp
) {
}
