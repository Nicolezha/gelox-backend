-- ============================================================================
-- V6__recomendacion_compra.sql
-- Tablas del módulo de recomendación de compras (RF54-RF57).
-- Estados como VARCHAR con CHECK (no tipos ENUM de PostgreSQL).
-- ============================================================================

CREATE TABLE IF NOT EXISTS recomendacion_compra (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID          NOT NULL REFERENCES distribuidora(id),
    usuario_id          UUID          NOT NULL REFERENCES usuario(id),
    presupuesto         NUMERIC(15,2) NOT NULL CHECK (presupuesto > 0),
    costo_total         NUMERIC(15,2) NOT NULL,
    costo_aprobado      NUMERIC(15,2) NOT NULL DEFAULT 0,
    estado              VARCHAR(20)   NOT NULL
                        CONSTRAINT ck_recomendacion_estado
                        CHECK (estado IN ('GENERADA', 'EN_REVISION', 'PEDIDO_GENERADO', 'DESCARTADA')),
    origen              VARCHAR(10)   NOT NULL
                        CONSTRAINT ck_recomendacion_origen CHECK (origen IN ('PANTALLA', 'VOZ')),
    confianza_baja      BOOLEAN       NOT NULL,
    dias_historial      INTEGER       NOT NULL,
    dias_con_ventas     INTEGER       NOT NULL,
    pedido_proveedor_id UUID          UNIQUE REFERENCES pedido_proveedor(id),
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS item_recomendacion (
    id                    UUID PRIMARY KEY,
    recomendacion_id      UUID          NOT NULL REFERENCES recomendacion_compra(id) ON DELETE CASCADE,
    producto_id           UUID          NOT NULL REFERENCES producto(id),
    prioridad             INTEGER       NOT NULL,
    urgencia              VARCHAR(10)   NOT NULL
                          CONSTRAINT ck_item_recomendacion_urgencia
                          CHECK (urgencia IN ('CRITICO', 'ALERTA', 'ESTABLE')),
    score                 NUMERIC(5,2)  NOT NULL,
    demanda_diaria        NUMERIC(10,3) NOT NULL,
    factor_estacionalidad NUMERIC(4,2)  NOT NULL,
    stock_seguridad       NUMERIC(10,2) NOT NULL,
    punto_reorden         NUMERIC(10,2) NOT NULL,
    stock_actual          INTEGER       NOT NULL,
    cobertura_dias        NUMERIC(8,2),
    unidades_sugeridas    INTEGER       NOT NULL,
    cajas_sugeridas       INTEGER       NOT NULL,
    ajuste_presupuesto    VARCHAR(10)   NOT NULL
                          CONSTRAINT ck_item_recomendacion_ajuste
                          CHECK (ajuste_presupuesto IN ('COMPLETO', 'REDUCIDO', 'EXCLUIDO')),
    costo_estimado        NUMERIC(15,2) NOT NULL,
    margen_esperado       NUMERIC(15,2) NOT NULL,
    justificacion         TEXT          NOT NULL,
    decision              VARCHAR(12)   NOT NULL DEFAULT 'PENDIENTE'
                          CONSTRAINT ck_item_recomendacion_decision
                          CHECK (decision IN ('PENDIENTE', 'APROBADO', 'MODIFICADO', 'RECHAZADO')),
    cajas_aprobadas       INTEGER,
    motivo_rechazo        TEXT,
    fecha_decision        TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_recomendacion_compra_tenant_fecha
    ON recomendacion_compra (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_item_recomendacion_recomendacion
    ON item_recomendacion (recomendacion_id);
