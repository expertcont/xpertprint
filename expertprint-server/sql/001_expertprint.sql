-- Ejecutar en PostgreSQL existente. No cambia tablas de ventas ni manifiestos.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TABLE IF NOT EXISTS public.mve_transprint_device (
 id_dispositivo UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 id_usuario VARCHAR(50) NOT NULL,
 documento_id VARCHAR(20) NOT NULL,
 id_punto_venta VARCHAR(10) NOT NULL,
 nombre VARCHAR(100) NOT NULL,
 token_hash CHAR(64) NOT NULL UNIQUE,
 activo BOOLEAN NOT NULL DEFAULT true,
 ultima_conexion TIMESTAMPTZ,
 creado TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY (id_usuario, documento_id, id_punto_venta)
  REFERENCES public.mad_punto_venta(id_usuario, documento_id, id_punto_venta)
);
CREATE TABLE IF NOT EXISTS public.mve_transprint (
 id_trabajo BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 id_usuario VARCHAR(50) NOT NULL,
 documento_id VARCHAR(20) NOT NULL,
 id_punto_venta VARCHAR(10) NOT NULL,
 id_dispositivo UUID REFERENCES public.mve_transprint_device(id_dispositivo),
 tipo_documento VARCHAR(12) NOT NULL CHECK (tipo_documento IN ('BOLETO','MANIFIESTO')),
 tipo_impresora VARCHAR(8) NOT NULL CHECK (tipo_impresora IN ('TERMICA','A4')),
 periodo VARCHAR(7) NOT NULL,
 referencia JSONB NOT NULL,
 archivo_pdf TEXT NOT NULL,
 estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','ASIGNADO','EN_COLA','ENVIADO','ERROR','INCIERTO','CANCELADO')),
 idempotency_key VARCHAR(150) NOT NULL,
 intentos INTEGER NOT NULL DEFAULT 0,
 lease_hasta TIMESTAMPTZ,
 creado TIMESTAMPTZ NOT NULL DEFAULT now(),
 actualizado TIMESTAMPTZ NOT NULL DEFAULT now(),
 ultimo_error TEXT,
 UNIQUE(id_usuario, documento_id, idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_print_pending ON public.mve_transprint(id_usuario, documento_id, id_punto_venta, creado) WHERE estado='PENDIENTE';
CREATE OR REPLACE FUNCTION public.fve_transprint_notify() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_notify('expertprint', json_build_object('id_trabajo', NEW.id_trabajo,'id_usuario',NEW.id_usuario,'documento_id',NEW.documento_id,'id_punto_venta',NEW.id_punto_venta)::text);
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_transprint_notify ON public.mve_transprint;
CREATE TRIGGER trg_transprint_notify AFTER INSERT ON public.mve_transprint FOR EACH ROW EXECUTE FUNCTION public.fve_transprint_notify();
-- Alta manual de un dispositivo: generar token con `openssl rand -hex 32`, almacenar digest(token,'sha256') en hexadecimal.
-- INSERT INTO public.mve_transprint_device(id_usuario,documento_id,id_punto_venta,nombre,token_hash)
-- VALUES ('USUARIO','RUC','AGENCIA','PC CAJA',encode(digest('TOKEN_GENERADO','sha256'),'hex'));
