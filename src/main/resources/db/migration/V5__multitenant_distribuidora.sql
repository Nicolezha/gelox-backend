-- ============================================================================
-- V5__multitenant_distribuidora.sql
-- Esquema multi-tenant: tabla distribuidora y tenant_id en las tablas raíz.
-- Todo el dato existente queda asignado a Mágico Sabor (tenant piloto).
-- ANTES DE DESPLEGAR: respaldo de la base (esta migración toca todas las tablas).
-- ============================================================================

-- 1. Distribuidoras
CREATE TABLE IF NOT EXISTS distribuidora (
    id                 UUID PRIMARY KEY,
    razon_social       VARCHAR(150) NOT NULL,
    nit                VARCHAR(20)  NOT NULL UNIQUE,
    nombre_comercial   VARCHAR(100) NOT NULL,
    subdominio         VARCHAR(30)  NOT NULL UNIQUE
                       CONSTRAINT ck_distribuidora_subdominio CHECK (subdominio ~ '^[a-z0-9-]{3,30}$'),
    correo_gerente     VARCHAR(150) NOT NULL,
    gerente_titular_id UUID REFERENCES usuario(id),
    telefono           VARCHAR(20),
    direccion          TEXT,
    barrio             VARCHAR(80),
    ciudad             VARCHAR(80),
    plan               VARCHAR(30)  NOT NULL,
    estado             VARCHAR(20)  NOT NULL
                       CONSTRAINT ck_distribuidora_estado CHECK (estado IN ('PRUEBA', 'ACTIVO', 'SUSPENDIDO', 'INACTIVO')),
    motivo_estado      TEXT,
    fecha_estado       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    logo_url           TEXT,
    favicon_url        TEXT,
    color_primario     CHAR(7)      NOT NULL
                       CONSTRAINT ck_distribuidora_color_primario CHECK (color_primario ~ '^#[0-9A-Fa-f]{6}$'),
    color_secundario   CHAR(7)      NOT NULL
                       CONSTRAINT ck_distribuidora_color_secundario CHECK (color_secundario ~ '^#[0-9A-Fa-f]{6}$'),
    eslogan            VARCHAR(150),
    whatsapp           VARCHAR(20),
    horario            VARCHAR(100),
    maps_url           TEXT,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Tenant piloto: Mágico Sabor (datos de negocio.* de application.properties).
-- nit, correo_gerente y plan son valores piloto: reemplazarlos por los reales con
-- UPDATE distribuidora SET ... WHERE subdominio = 'magicosabor'.
INSERT INTO distribuidora (id, razon_social, nit, nombre_comercial, subdominio, correo_gerente,
                           direccion, barrio, ciudad, plan, estado, color_primario, color_secundario,
                           whatsapp, horario, maps_url)
VALUES ('00000000-0000-0000-0000-000000000001',
        'Mágico Sabor',
        '900000001',
        'Mágico Sabor',
        'magicosabor',
        'gerente@magicosabor.test',
        'Calle 0 Av 9 #9-36',
        'Carora',
        'Cúcuta',
        'PILOTO',
        'ACTIVO',
        '#9E2016',
        '#7C0202',
        '573124099628',
        'Lunes a Domingo 10:00am - 9:00pm',
        'https://maps.app.goo.gl/mbuxwjHGfQZjZXoF7')
ON CONFLICT (id) DO NOTHING;

-- 3. tenant_id en cada tabla raíz: columna, datos, NOT NULL (salvo usuario), FK e índice.
-- Las tablas con índice compuesto propio (sección 5) no llevan el índice simple.
DO $$
DECLARE
    t TEXT;
    tablas TEXT[] := ARRAY[
        'producto', 'venta', 'pedido_proveedor', 'comerciante',
        'planilla_comerciante', 'cliente_rural', 'pedido_rural', 'cierre_caja',
        'movimiento_inventario', 'perdida', 'evento_sistema', 'comando_voz', 'usuario'
    ];
    con_indice_compuesto TEXT[] := ARRAY[
        'producto', 'venta', 'pedido_proveedor', 'comerciante',
        'planilla_comerciante', 'cierre_caja', 'movimiento_inventario', 'perdida', 'evento_sistema'
    ];
BEGIN
    FOREACH t IN ARRAY tablas LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id UUID', t);

        IF t = 'usuario' THEN
            -- El SUPERADMINISTRADOR de plataforma no pertenece a ninguna distribuidora
            EXECUTE format('UPDATE %I SET tenant_id = %L WHERE tenant_id IS NULL AND rol <> %L',
                           t, '00000000-0000-0000-0000-000000000001', 'SUPERADMINISTRADOR');
        ELSE
            EXECUTE format('UPDATE %I SET tenant_id = %L WHERE tenant_id IS NULL',
                           t, '00000000-0000-0000-0000-000000000001');
            EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', t);
        END IF;

        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', t, 'fk_' || t || '_tenant');
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I FOREIGN KEY (tenant_id) REFERENCES distribuidora(id)',
                       t, 'fk_' || t || '_tenant');

        IF NOT (t = ANY (con_indice_compuesto)) THEN
            EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (tenant_id)', 'idx_' || t || '_tenant', t);
        END IF;
    END LOOP;
END $$;

-- 4. Restricciones que cambian
-- usuario: el rol admite SUPERADMINISTRADOR, y solo él puede no tener distribuidora
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check
    CHECK (rol IN ('SUPERADMINISTRADOR', 'ADMINISTRADOR', 'ENCARGADO_INVENTARIO', 'ENCARGADO_VENTAS'));
ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_tenant_superadmin_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_tenant_superadmin_check
    CHECK ((rol = 'SUPERADMINISTRADOR') = (tenant_id IS NULL));

-- producto: el código técnico pasa a ser único por distribuidora
ALTER TABLE producto DROP CONSTRAINT IF EXISTS producto_codigo_tecnico_key;
ALTER TABLE producto DROP CONSTRAINT IF EXISTS uq_producto_tenant_codigo;
ALTER TABLE producto ADD CONSTRAINT uq_producto_tenant_codigo UNIQUE (tenant_id, codigo_tecnico);

-- cierre_caja: cada distribuidora cierra su propia caja diaria (las dos únicas por fecha eran duplicadas)
ALTER TABLE cierre_caja DROP CONSTRAINT IF EXISTS cierre_caja_fecha_key;
ALTER TABLE cierre_caja DROP CONSTRAINT IF EXISTS uk36iv6b17iambfc9yh8s35rsgj;
ALTER TABLE cierre_caja DROP CONSTRAINT IF EXISTS uq_cierre_caja_tenant_fecha;
ALTER TABLE cierre_caja ADD CONSTRAINT uq_cierre_caja_tenant_fecha UNIQUE (tenant_id, fecha);

-- 5. Índices compuestos
CREATE INDEX IF NOT EXISTS idx_producto_tenant_activo          ON producto (tenant_id, activo);
CREATE INDEX IF NOT EXISTS idx_venta_tenant_fecha              ON venta (tenant_id, fecha);
CREATE INDEX IF NOT EXISTS idx_pedido_proveedor_tenant_fecha   ON pedido_proveedor (tenant_id, fecha);
CREATE INDEX IF NOT EXISTS idx_comerciante_tenant_activo       ON comerciante (tenant_id, activo);
CREATE INDEX IF NOT EXISTS idx_planilla_comerciante_tenant_fecha ON planilla_comerciante (tenant_id, fecha);
CREATE INDEX IF NOT EXISTS idx_movimiento_inventario_tenant_fecha ON movimiento_inventario (tenant_id, fecha);
CREATE INDEX IF NOT EXISTS idx_perdida_tenant_fecha            ON perdida (tenant_id, fecha);
CREATE INDEX IF NOT EXISTS idx_evento_sistema_tenant_fecha     ON evento_sistema (tenant_id, fecha DESC);

-- 6. El trigger de V4 inserta en evento_sistema: ahora debe propagar tenant_id
CREATE OR REPLACE FUNCTION fn_evaluar_stock_alerta()
RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.stock_actual <= NEW.stock_minimo) AND (OLD.stock_actual > OLD.stock_minimo OR OLD.stock_actual IS NULL) THEN
        INSERT INTO evento_sistema (
            id,
            tipo,
            descripcion,
            usuario_id,
            fecha,
            tenant_id
        ) VALUES (
            gen_random_uuid(),
            'ALERTA_STOCK',
            'Alerta automática de inventario: El producto "' || NEW.nombre || '" (código: ' || NEW.codigo_tecnico ||
            ') ha alcanzado nivel crítico de inventario. Stock actual: ' || NEW.stock_actual ||
            ' unidades, Stock mínimo: ' || NEW.stock_minimo || ' unidades.',
            NULL,
            CURRENT_TIMESTAMP,
            NEW.tenant_id
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 7. sp_calcular_cierre_caja (V4): agrega p_tenant_id y quita el filtro v.estado,
-- columna que no existe en venta. Cambia la firma, por eso se elimina la anterior.
DROP PROCEDURE IF EXISTS sp_calcular_cierre_caja;

CREATE OR REPLACE PROCEDURE sp_calcular_cierre_caja(
    IN  p_fecha DATE,
    IN  p_tenant_id UUID,
    OUT p_total_ventanilla NUMERIC,
    OUT p_total_rural NUMERIC,
    OUT p_total_comerciantes NUMERIC,
    OUT p_total_general NUMERIC,
    OUT p_transacciones INT
)
LANGUAGE plpgsql
AS $$
BEGIN
    SELECT
        COALESCE(SUM(CASE WHEN v.canal = 'VENTANILLA' THEN v.total ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN v.canal = 'RURAL' THEN v.total ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN v.canal = 'COMERCIANTES' THEN v.total ELSE 0 END), 0),
        COALESCE(SUM(v.total), 0),
        COUNT(v.id)
    INTO
        p_total_ventanilla,
        p_total_rural,
        p_total_comerciantes,
        p_total_general,
        p_transacciones
    FROM venta v
    WHERE v.tenant_id = p_tenant_id
      AND DATE(v.fecha) = p_fecha;
END;
$$;
