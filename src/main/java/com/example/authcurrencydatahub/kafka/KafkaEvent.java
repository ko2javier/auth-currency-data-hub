package com.example.authcurrencydatahub.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEvent {

    private String eventType;
    private String username;
    private String clientIp;

    @Builder.Default
    private Instant timestamp = Instant.now();

    private Map<String, String> metadata;
}
