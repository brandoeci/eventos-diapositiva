package edu.eci.arsw.rediseda.producer;

import edu.eci.arsw.rediseda.dto.TransferenciaCreada;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static edu.eci.arsw.rediseda.config.RedisConfig.STREAM_KEY;

@Component
public class TransferenciaProducer {

    private final StringRedisTemplate redisTemplate;

    public TransferenciaProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String publicar(TransferenciaCreada evento) {
        var record = StreamRecords.newRecord()
                .in(STREAM_KEY)
                .ofMap(Map.of(
                        "eventId",    evento.eventId(),
                        "transferId", evento.transferId(),
                        "from",       evento.from(),
                        "to",         evento.to(),
                        "amount",     evento.amount(),
                        "currency",   evento.currency(),
                        "createdAt",  evento.createdAt()
                ));

        var id = redisTemplate.opsForStream().add(record);
        System.out.println("[PRODUCTOR] Evento publicado con ID: " + id);
        return id.getValue();
    }
}