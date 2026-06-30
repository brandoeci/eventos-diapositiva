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

<img width="1043" height="226" alt="docker compose up" src="https://github.com/user-attachments/assets/5cc2380a-245c-4e45-afb9-e0762c34e6c7" />

*Redis levantado correctamente con docker compose.*

### 2. Ejecutar la aplicación desde IntelliJ

Run sobre `RedisEDAApplication`. Al iniciar, la app crea los tres grupos de
consumidores y registra los listeners sobre el stream.

<img width="598" height="114" alt="aplicación iniciada" src="https://github.com/user-attachments/assets/b43e1bfa-e4fd-4370-98aa-2ed790b67e75" />

*Aplicación iniciada, grupos de consumidores registrados.*

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

<img width="552" height="32" alt="post transferencia" src="https://github.com/user-attachments/assets/09a84453-6995-44d1-83ee-785373544d85" />

*Respuesta del POST al crear la transferencia.*

### 4. Verificar el flujo completo en los logs

<img width="1768" height="173" alt="logs flujo normal" src="https://github.com/user-attachments/assets/f14b67c6-3953-472a-ac24-53b391d54f19" />

*Logs mostrando a los tres consumidores procesando el evento.*

### 5. Activar la simulación de caída en Auditoría

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/transferencias/auditoria/caida?activa=true" -Method Patch
```

<img width="1592" height="55" alt="activar caída" src="https://github.com/user-attachments/assets/80d9b8fa-9b48-44c7-8526-ce7ae7d99473" />

*Flag de caída activado vía endpoint PATCH.*

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

<img width="565" height="28" alt="logs caída simulada" src="https://github.com/user-attachments/assets/73b9456e-1fe4-4caa-846c-2d2efc53a734" />

*El consumidor de auditoría recibe el evento pero no confirma con `XACK`.*

### 7. Verificar el mensaje pendiente

```powershell
docker exec -it redis-eda redis-cli
XPENDING banco.transferencias auditoria-group - + 10
```

<img width="1758" height="61" alt="xpending" src="https://github.com/user-attachments/assets/238747f5-9a98-4e53-bbae-f74bd951847b" />

*El mensaje queda registrado como pendiente sin confirmar.*

### 8. Reclamar el mensaje pendiente (recuperación tras la caída)

```
XCLAIM banco.transferencias auditoria-group consumer-1 0 <ID-del-mensaje>
```

*(Pendiente: captura del redis-cli mostrando el contenido completo del evento reclamado.)*

### 9. Confirmar manualmente el mensaje

```
XACK banco.transferencias auditoria-group <ID-del-mensaje>
```

<img width="920" height="485" alt="xack manual" src="https://github.com/user-attachments/assets/dcbcbdb2-8e17-4966-9960-b4428d049393" />

*Confirmación manual del mensaje reclamado.*

### 10. Verificar que ya no queden pendientes

```
XPENDING banco.transferencias auditoria-group - + 10
```

<img width="747" height="80" alt="xpending vacío" src="https://github.com/user-attachments/assets/bba458fc-238a-43e2-abc6-6655590b8763" />

*Lista de pendientes vacía: el mensaje fue confirmado correctamente.*

## Conclusiones

- El stream conserva el evento aunque un consumidor "caiga" antes del ACK,
  cumpliendo el principio de diseño para fallos de la diapositiva 4.
- Cada grupo de consumidores procesa su propia copia lógica del stream,
  sin competir entre sí (diapositiva 5).
- `XPENDING` + `XCLAIM` permiten detectar y recuperar mensajes no confirmados,
  habilitando reintentos sin pérdida de eventos.
