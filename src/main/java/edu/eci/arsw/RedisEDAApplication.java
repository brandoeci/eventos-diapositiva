package edu.eci.arsw.rediseda;

import edu.eci.arsw.rediseda.consumer.AuditoriaConsumer;
import edu.eci.arsw.rediseda.consumer.FraudeConsumer;
import edu.eci.arsw.rediseda.consumer.NotifConsumer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.connection.stream.MapRecord;

import static edu.eci.arsw.rediseda.config.RedisConfig.STREAM_KEY;
import static edu.eci.arsw.rediseda.config.RedisConfig.GRUPOS;

@SpringBootApplication
public class RedisEDAApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisEDAApplication.class, args);
    }

    @Bean
    CommandLineRunner init(
            StringRedisTemplate redisTemplate,
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            FraudeConsumer fraude,
            NotifConsumer notif,
            AuditoriaConsumer auditoria
    ) {
        return args -> {
            // Crear grupos (ignora error si ya existen)
            for (String grupo : GRUPOS) {
                try {
                    redisTemplate.opsForStream()
                            .createGroup(STREAM_KEY, ReadOffset.from("0"), grupo);
                    System.out.println("[INIT] Grupo creado: " + grupo);
                } catch (Exception e) {
                    System.out.println("[INIT] Grupo ya existe: " + grupo);
                }
            }

            // Registrar consumidores
            container.receive(
                    Consumer.from("fraude-group", "consumer-1"),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    fraude);

            container.receive(
                    Consumer.from("notif-group", "consumer-1"),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    notif);

            container.receive(
                    Consumer.from("auditoria-group", "consumer-1"),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                    auditoria);

            container.start();
            System.out.println("[INIT] Listeners activos en stream: " + STREAM_KEY);
        };
    }
}