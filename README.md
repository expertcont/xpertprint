# ExpertPrint

Monorepositorio inicial para la impresión remota de Expertcont.

## Carpetas
- `expertprint-server/`: backend Quarkus Java 21, **reutilizado del starter original**.
- `expertprint-agent/`: espacio reservado para el agente Windows C# .NET 8.
- `.github/workflows/`: compilación de backend en GitHub Actions (Java 21).
- `docs/`: documentación.
- `scripts/`: futuros scripts de despliegue.

## Estado actual
El backend contiene endpoints para trabajos de impresión, SQL y ejemplos Express/React, pero **no está validado ni listo para producción**. La compilación y pruebas no han sido ejecutadas al crear este paquete. El starter requiere PostgreSQL y credenciales para su ejecución normal.

**No ejecutar automáticamente** `sql/001_expertprint.sql`, `docker compose up` ni configurar acceso público. Antes del despliegue hay que revisar seguridad, autenticación, aislamiento de red, configuración PostgreSQL, recuperación de trabajos y pruebas.

## Windows MiniOS
No hace falta Docker Desktop, Java ni Maven local para editar el código. GitHub Actions puede compilar en Linux una vez subido el repositorio. El VPS IONOS se utilizará para despliegue solo con autorización.

## Inicio
1. Extraer este ZIP en una carpeta `ExpertPrint`.
2. Abrir esa carpeta con Cursor/Codex.
3. Revisar `expertprint-server/README.md` y el código.
4. Inicializar Git y enlazar el repositorio privado de GitHub cuando corresponda.
5. Solicitar a Codex que revise y corrija el starter antes de desplegar.
