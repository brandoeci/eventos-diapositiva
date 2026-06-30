# Arquitectura orientada por eventos con Redis Streams

Implementación del ejemplo "Banco y una transferencia creada" usando Spring Boot
y Redis Streams, incluyendo la actividad sugerida (grupo de auditoría + simulación
de caída de un consumidor).

## Arquitectura

```
API REST  →  Productor (XADD)  →  Redis Stream: banco.transferencias
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
            fraude-group           notif-group           auditoria-group
            (XREADGROUP+ACK)       (XREADGROUP+ACK)      (XREADGROUP+ACK)
```

Cada grupo de consumidores procesa el mismo evento de forma independiente,
sin acoplarse entre sí ni con el productor.

## Evento de negocio

```json
{
  "eventId": "uuid-generado",
  "transferId": "tr-987",
  "from": "cta-101",
  "to": "cta-202",
  "amount": "150000",
  "currency": "COP",
  "createdAt": "2026-06-30T02:10:10Z"
}
```

## Componentes implementados

- **Productor**: `TransferenciaProducer` — publica el evento con `XADD`.
- **Consumidores**: `FraudeConsumer`, `NotifConsumer`, `AuditoriaConsumer` — cada
  uno en su propio grupo, procesando y confirmando con `XACK`.
- **Simulación de caída**: `AuditoriaConsumer` tiene un flag controlado por
  endpoint que omite el `XACK`, dejando el mensaje pendiente para reproducir
  el escenario de la diapositiva 9.

## Pasos para ejecutar

### 1. Levantar Redis

```powershell
docker compose up -d
```

**[CAPTURA-1-DOCKER-COMPOSE-UP]**

<img width="1043" height="226" alt="image" src="https://github.com/user-attachments/assets/5cc2380a-245c-4e45-afb9-e0762c34e6c7" />

### 2. Ejecutar la aplicación desde IntelliJ

Run sobre `RedisEDAApplication`. Al iniciar, la app crea los tres grupos de
consumidores y registra los listeners sobre el stream.

**[CAPTURA-2-APP-INICIADA]**

<img width="598" height="114" alt="image" src="https://github.com/user-attachments/assets/b43e1bfa-e4fd-4370-98aa-2ed790b67e75" />


### 3. Publicar una transferencia (flujo normal)

```powershell
$body = @{
    transferId = "tr-987"
    from = "cta-101"
    to = "cta-202"
    amount = "150000"
    currency = "COP"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/transferencias" -Method Post -Body $body -ContentType "application/json"
```

**[CAPTURA-3-POST-TRANSFERENCIA]**

<img width="552" height="32" alt="image" src="https://github.com/user-attachments/assets/09a84453-6995-44d1-83ee-785373544d85" />

### 4. Verificar el flujo completo en los logs

**[CAPTURA-4-LOGS-FLUJO-NORMAL]**

<img width="1768" height="173" alt="image" src="https://github.com/user-attachments/assets/f14b67c6-3953-472a-ac24-53b391d54f19" />

### 5. Activar la simulación de caída en Auditoría

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/transferencias/auditoria/caida?activa=true" -Method Patch
```

**[CAPTURA-5-ACTIVAR-CAIDA]**

<img width="1592" height="55" alt="image" src="https://github.com/user-attachments/assets/80d9b8fa-9b48-44c7-8526-ce7ae7d99473" />

### 6. Publicar una nueva transferencia y observar la caída simulada

```powershell
$body = @{
    transferId = "tr-988"
    from = "cta-101"
    to = "cta-202"
    amount = "75000"
    currency = "COP"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/transferencias" -Method Post -Body $body -ContentType "application/json"
```

**[CAPTURA-6-LOGS-CAIDA-SIMULADA]**

<img width="565" height="28" alt="image" src="https://github.com/user-attachments/assets/73b9456e-1fe4-4caa-846c-2d2efc53a734" />


### 7. Verificar el mensaje pendiente

```powershell
docker exec -it redis-eda redis-cli
XPENDING banco.transferencias auditoria-group - + 10
```

**[CAPTURA-7-XPENDING]**

<img width="1758" height="61" alt="image" src="https://github.com/user-attachments/assets/238747f5-9a98-4e53-bbae-f74bd951847b" />

### 8. Reclamar el mensaje pendiente (recuperación tras la caída)

```
XCLAIM banco.transferencias auditoria-group consumer-1 0 <ID-del-mensaje>
```

**[CAPTURA-8-XCLAIM]**
*(redis-cli mostrando el contenido completo del evento reclamado)*

### 9. Confirmar manualmente el mensaje

```
XACK banco.transferencias auditoria-group <ID-del-mensaje>
```

**[CAPTURA-9-XACK-MANUAL]**

<img width="920" height="485" alt="image" src="https://github.com/user-attachments/assets/dcbcbdb2-8e17-4966-9960-b4428d049393" />

### 10. Verificar que ya no queden pendientes

```
XPENDING banco.transferencias auditoria-group - + 10
```

**[CAPTURA-10-XPENDING-VACIO]**

<img width="747" height="80" alt="image" src="https://github.com/user-attachments/assets/bba458fc-238a-43e2-abc6-6655590b8763" />

## Conclusiones

- El stream conserva el evento aunque un consumidor "caiga" antes del ACK,
  cumpliendo el principio de diseño para fallos de la diapositiva 4.
- Cada grupo de consumidores procesa su propia copia lógica del stream,
  sin competir entre sí (diapositiva 5).
- `XPENDING` + `XCLAIM` permiten detectar y recuperar mensajes no confirmados,
  habilitando reintentos sin pérdida de eventos.
