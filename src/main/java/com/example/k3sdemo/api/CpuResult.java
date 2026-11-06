package com.example.k3sdemo.api;

public record CpuResult(
        long requestedMillis,
        long actualMillis,
        double checksum,
        String podName
) {
}
