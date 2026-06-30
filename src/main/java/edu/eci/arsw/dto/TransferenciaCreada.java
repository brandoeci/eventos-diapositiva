package edu.eci.arsw.rediseda.dto;

public record TransferenciaCreada(
        String eventId,
        String transferId,
        String from,
        String to,
        String amount,
        String currency,
        String createdAt
) {}