# ExpertPrint — Quarkus Java 21 (Fase 1)

**Starter real** para comenzar el backend de cola de impresión. No es aún un sistema terminado ni listo para producción.

## Requisitos
- Java 21, Maven 3.9 (o Docker para construir sin instalar Maven)
- PostgreSQL existente de Expertcont
- Docker y Docker Compose en VPS

## Puesta en marcha
1. Revisar y ejecutar `sql/001_expertprint.sql` en PostgreSQL (primero respaldar BD).
2. Copiar `.env.example` a `.env` y ajustar URL JDBC y credenciales.
3. `docker compose up -d --build`
4. Comprobar `curl http://127.0.0.1:8088/q/health` en el VPS.
5. Crear token aleatorio de dispositivo `openssl rand -hex 32` y registrar SHA-256 en la tabla device (ejemplo comentado en SQL).
6. Integrar Express según `express/print-proxy.example.js`, **después** de agregar autorización real.
7. Adaptar el generador React para devolver bytes, como indica `react/print-boleto.example.js`.

## API disponible
- `POST /api/internal/jobs`: recibe bytes `application/pdf` de Express; metadatos en headers; llave interna.
- `GET /api/device/jobs`: lista pendientes de la agencia del dispositivo.
- `POST /api/device/jobs/{jobId}/claim`: reclama de forma atómica.
- `GET /api/device/jobs/{jobId}/pdf`: descarga PDF autenticado.
- `POST /api/device/jobs/{jobId}/result`: reporta estado.

Headers del dispositivo: `X-Device-Id`, `X-Device-Token`.

## Advertencias importantes
- La versión 1 usa **polling**, no WebSockets todavía. Se agregará WebSockets Next y listener LISTEN/NOTIFY en fase 2.
- El agente C# Windows todavía no está incluido: fase 3.
- Un trabajo `ASIGNADO` cuyo lease expiró NO se reencola automáticamente. Hace falta protocolo seguro de recuperación para evitar duplicados.
- El endpoint `EN_COLA` es un estado de transición; no garantiza impresión física.
- El endpoint interno debe estar detrás de HTTPS o red privada, jamás expuesto con su clave al navegador.
- En esta fase los PDF se guardan en un volumen persistente. Definir retención y borrado seguro por privacidad.
- **Importante**: el ejemplo Express NO incluye validación real de Auth0 ni verifica que el boleto pertenece al usuario; se debe implementar antes de habilitarlo.
- Las solicitudes repetidas con la misma idempotency key devuelven el mismo trabajo.
- El archivo se escribe antes de insertar el registro; ante caída entre operaciones podría quedar un archivo huérfano. Agregar limpieza periódica.
- No se ha ejecutado una compilación Maven en este entorno; verificar compatibilidad de dependencias con `docker compose build`.

## Próximas fases
- Fase 2: WebSockets Next, listener dedicado PostgreSQL LISTEN/NOTIFY, recuperación segura, auditoría y pruebas.
- Fase 3: C# .NET 8 WinForms tray app, configuración de térmica/A4, spool, cola local persistente.
- Fase 4: PDF A4 preimpreso de manifiesto, una vez se proporcione la plantilla física.
