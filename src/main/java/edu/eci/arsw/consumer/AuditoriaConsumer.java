package edu.eci.arsw.rediseda.consumer;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import static edu.eci.arsw.rediseda.config.RedisConfig.STREAM_KEY;

@Component
public class AuditoriaConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StringRedisTemplate redisTemplate;

    // Cambia a true para simular la caída antes del ACK
    private boolean simularCaida = false;

    public AuditoriaConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setSimularCaida(boolean valor) {
        this.simularCaida = valor;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        System.out.println("[AUDITORIA] Guardando evento: " + message.getValue());

        if (simularCaida) {
            System.out.println("[AUDITORIA] *** CAÍDA SIMULADA — no se enviará ACK ***");
            // El mensaje queda pendiente en el grupo
            return;
        }

        redisTemplate.opsForStream().acknowledge(STREAM_KEY, "auditoria-group", message.getId());
        System.out.println("[AUDITORIA] ACK enviado: " + message.getId());
    }
}