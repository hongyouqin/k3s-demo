package com.example.k3sdemo.api;

import com.example.k3sdemo.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
public class DemoController {

    private final AppProperties properties;
    private final RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    private final AtomicLong requestCounter = new AtomicLong();

    public DemoController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/")
    public Map<String, String> index() {
        return Map.of(
                "message", "Spring Boot on k3s is running",
                "pod", properties.podName(),
                "info", "/api/info",
                "hello", "/api/hello?name=k3s",
                "cpuLoad", "/api/cpu?millis=500",
                "health", "/actuator/health"
        );
    }

    @GetMapping("/api/info")
    public PodInfo info() {
        return new PodInfo(
                properties.name(),
                properties.version(),
                properties.podName(),
                properties.podIp(),
                properties.podNamespace(),
                properties.nodeName(),
                System.getProperty("java.version"),
                Instant.ofEpochMilli(runtimeMXBean.getStartTime())
        );
    }

    @GetMapping("/api/hello")
    public HelloResponse hello(@RequestParam(defaultValue = "k3s") String name) {
        return new HelloResponse(
                "%s, %s!".formatted(properties.greeting(), name),
                properties.podName(),
                requestCounter.incrementAndGet(),
                Instant.now()
        );
    }

    /**
     * Burns CPU for a bounded amount of time.
     *
     * <p>This endpoint exists only to make HorizontalPodAutoscaler scaling easy
     * to observe. Do not expose it as a real production API.
     */
    @GetMapping("/api/cpu")
    public CpuResult cpuLoad(@RequestParam(defaultValue = "500") long millis) {
        long requestedMillis = Math.max(1, Math.min(millis, 5_000));
        long started = System.nanoTime();
        long deadline = started + Duration.ofMillis(requestedMillis).toNanos();

        double checksum = 0.0;
        while (System.nanoTime() < deadline) {
            checksum += Math.sqrt(checksum + 1.0);
        }

        long actualMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();
        return new CpuResult(requestedMillis, actualMillis, checksum, properties.podName());
    }
}
