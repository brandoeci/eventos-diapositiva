package edu.eci.arsw.rediseda.controller;

import edu.eci.arsw.rediseda.consumer.AuditoriaConsumer;
import edu.eci.arsw.rediseda.dto.TransferenciaCreada;
import edu.eci.arsw.rediseda.producer.TransferenciaProducer;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/transferencias")
public class TransferenciaController {

    private final TransferenciaProducer producer;
    private final AuditoriaConsumer auditoriaConsumer;

    public TransferenciaController(TransferenciaProducer producer, AuditoriaConsumer auditoriaConsumer) {
        this.producer = producer;
        this.auditoriaConsumer = auditoriaConsumer;
    }

    @PostMapping
    public String crearTransferencia(@RequestBody TransferenciaCreada body) {
        var evento = new TransferenciaCreada(
                UUID.randomUUID().toString(),
                body.transferId(),
                body.from(),
                body.to(),
                body.amount(),
                body.currency(),
                Instant.now().toString()
        );
        return producer.publicar(evento);
    }

    // Activa/desactiva la simulación de caída en Auditoría
    @PatchMapping("/auditoria/caida")
    public String toggleCaida(@RequestParam boolean activa) {
        auditoriaConsumer.setSimularCaida(activa);
        return "Simulación de caída: " + (activa ? "ACTIVA" : "DESACTIVADA");
    }
}