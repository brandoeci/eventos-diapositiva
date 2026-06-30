package edu.eci.arsw.rediseda.consumer;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import static edu.eci.arsw.rediseda.config.RedisConfig.STREAM_KEY;

@Component
public class FraudeConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate redisTemplate;

    public FraudeConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        System.out.println("[FRAUDE] Analizando transferencia: " + message.getValue());
        redisTemplate.opsForStream().acknowledge(STREAM_KEY, "fraude-group", message.getId());
        System.out.println("[FRAUDE] ACK enviado: " + message.getId());
    }
}