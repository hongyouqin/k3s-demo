package com.example.k3sdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.version=test",
        "app.greeting=Hello"
})
@AutoConfigureMockMvc
class K3sDemoApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infoEndpointReturnsPodMetadata() throws Exception {
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("k3s-demo"))
                .andExpect(jsonPath("$.version").value("test"))
                .andExpect(jsonPath("$.podName").isNotEmpty())
                .andExpect(jsonPath("$.namespace").isNotEmpty())
                .andExpect(jsonPath("$.startedAt").isNotEmpty());
    }

    @Test
    void helloUsesConfiguredGreeting() throws Exception {
        mockMvc.perform(get("/api/hello").param("name", "K3s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello, K3s!"))
                .andExpect(jsonPath("$.podName").isNotEmpty())
                .andExpect(jsonPath("$.requestNumber").isNumber());
    }

    @Test
    void cpuEndpointReturnsBoundedResult() throws Exception {
        mockMvc.perform(get("/api/cpu").param("millis", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedMillis").value(1))
                .andExpect(jsonPath("$.podName").isNotEmpty());
    }

    @Test
    void healthAndReadinessProbesAreUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
