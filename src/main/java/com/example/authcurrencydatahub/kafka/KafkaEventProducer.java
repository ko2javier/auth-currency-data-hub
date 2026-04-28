package com.example.authcurrencydatahub.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventProducer {

    private final KafkaTemplate<String, KafkaEvent> kafkaTemplate;

    @Value("${kafka.topics.user-login}")
    private String topicUserLogin;

    public void publishUserLogin(String username, String clientIp) {
        KafkaEvent event = KafkaEvent.builder()
                .eventType("USER_LOGIN")
                .username(username)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .metadata(Map.of("detail", "Login exitoso"))
                .build();
        send(topicUserLogin, username, event);
    }

    private void send(String topic, String key, KafkaEvent event) {
        CompletableFuture<SendResult<String, KafkaEvent>> future =
                kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Error publicando evento {} en topic {}: {}",
                        event.getEventType(), topic, ex.getMessage());
            } else {
                log.debug("Evento {} publicado → topic={} partition={} offset={}",
                        event.getEventType(), topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
