-- ============================================================================
-- V10__credito_y_liquidacion.sql
-- RF65/RF66 — Políticas de crédito por comerciante, abonos y esquema de
-- liquidación parametrizable (FP-15 §2.17). Nota de alcance: esta migración
-- solo cubre crédito y liquidación (T189); las tablas de catálogo multimarca,
-- proveedores y exportación (T183/T188/T190) quedan en una migración aparte
-- cuando se construyan, avisando en el grupo antes de numerarla V11.
-- ============================================================================

-- 1. Política de crédito. comerciante_id NULL = política por defecto de la
--    distribuidora; una fila con comerciante_id = la excepción de ese comerciante.
--    Los índices únicos parciales evitan dos políticas por defecto o dos
--    excepciones para el mismo comerciante (NULL no es único por sí solo en SQL).
CREATE TABLE IF NOT EXISTS politica_credito (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID    NOT NULL REFERENCES distribuidora(id),
    comerciante_id       UUID REFERENCES comerciante(id),
    cupo                 NUMERIC(15,2) NOT NULL CONSTRAINT ck_politica_cupo CHECK (cupo >= 0),
    plazo_dias           INTEGER       NOT NULL CONSTRAINT ck_politica_plazo CHECK (plazo_dias >= 0),
    politica_vencimiento VARCHAR(15)   NOT NULL
        CONSTRAINT ck_politica_vencimiento CHECK (politica_vencimiento IN ('BLOQUEAR', 'SOLO_ALERTA')),
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_politica_credito_defecto
    ON politica_credito (tenant_id) WHERE comerciante_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_politica_credito_excepcion
    ON politica_credito (tenant_id, comerciante_id) WHERE comerciante_id IS NOT NULL;

-- Política por defecto de cada distribuidora existente (FP-15 §1.6.2.3: cupo
-- $50.000, plazo 0 días, SOLO_ALERTA).
INSERT INTO politica_credito (tenant_id, comerciante_id, cupo, plazo_dias, politica_vencimiento)
SELECT d.id, NULL, 50000, 0, 'SOLO_ALERTA'
FROM distribuidora d
ON CONFLICT DO NOTHING;

-- 2. Abonos de comerciantes. planilla_id NULL = abono general (no se aplica a
--    una planilla concreta); reduce la deuda agregada del comerciante.
CREATE TABLE IF NOT EXISTS abono_comerciante (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES distribuidora(id),
    comerciante_id UUID NOT NULL REFERENCES comerciante(id),
    planilla_id    UUID REFERENCES planilla_comerciante(id),
    fecha          DATE NOT NULL DEFAULT CURRENT_DATE,
    valor          NUMERIC(15,2) NOT NULL CONSTRAINT ck_abono_valor CHECK (valor > 0),
    usuario_id     UUID NOT NULL REFERENCES usuario(id),
    observacion    TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_abono_comerciante_comerciante ON abono_comerciante (comerciante_id, fecha DESC);

-- 3. Esquema de liquidación vigente por distribuidora (historial: la fila más
--    reciente por vigente_desde es la actual). MARGEN reproduce el cálculo de
--    hoy (precio al comerciante × unidades vendidas), así que valor queda en 0.
CREATE TABLE IF NOT EXISTS esquema_liquidacion (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES distribuidora(id),
    tipo          VARCHAR(12) NOT NULL
        CONSTRAINT ck_esquema_tipo CHECK (tipo IN ('MARGEN', 'COMISION', 'TARIFA_FIJA')),
    valor         NUMERIC(15,4) NOT NULL DEFAULT 0 CONSTRAINT ck_esquema_valor CHECK (valor >= 0),
    vigente_desde TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_esquema_liquidacion_tenant ON esquema_liquidacion (tenant_id, vigente_desde DESC);

INSERT INTO esquema_liquidacion (tenant_id, tipo, valor)
SELECT d.id, 'MARGEN', 0 FROM distribuidora d;

-- 4. Columnas nuevas de planilla_comerciante: ganancia_comerciante (lo que se
--    queda el comerciante, calculado al liquidar), esquema_aplicado y
--    valor_esquema (copia del esquema vigente al momento de liquidar, para que
--    un cambio posterior del esquema no altere planillas ya cerradas) y
--    fecha_limite_pago (fecha de la planilla + plazo de la política efectiva).
--    total_ganancia conserva su significado actual: el monto cobrado al
--    comerciante (no cambia con esta migración).
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS ganancia_comerciante NUMERIC(15,2);
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS esquema_aplicado VARCHAR(12);
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS valor_esquema NUMERIC(15,4);
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS fecha_limite_pago DATE;

-- Planillas ya cerradas: esquema_aplicado = MARGEN y, donde efectivo_recibido
-- es nulo, se asume que se cobró el total (comportamiento previo a esta
-- migración, en la que nadie llenaba efectivo_recibido).
UPDATE planilla_comerciante
   SET esquema_aplicado = 'MARGEN',
       efectivo_recibido = COALESCE(efectivo_recibido, total_ganancia)
 WHERE cerrada = true;

-- 5. Columnas nuevas de item_planilla: precio_publico (copiado al crear el
--    despacho) y ganancia_comerciante (calculado al liquidar, según el
--    esquema). precio_venta sigue siendo el precio al comerciante.
ALTER TABLE item_planilla ADD COLUMN IF NOT EXISTS precio_publico NUMERIC(15,2);
ALTER TABLE item_planilla ADD COLUMN IF NOT EXISTS ganancia_comerciante NUMERIC(15,2);
