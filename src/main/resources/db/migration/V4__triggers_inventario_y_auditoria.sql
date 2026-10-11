-- ============================================================================
-- V4__triggers_inventario_y_auditoria.sql
-- Optimización de cómputo en base de datos mediante Triggers y Stored Procedures
-- Dominio: Inventario y Auditoría de Eventos del Sistema
-- ============================================================================

-- 1. Función Trigger para actualización automática de timestamps
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Trigger para mantener updated_at en la tabla producto
DROP TRIGGER IF EXISTS trg_producto_updated_at ON producto;
CREATE TRIGGER trg_producto_updated_at
BEFORE UPDATE ON producto
FOR EACH ROW
EXECUTE FUNCTION fn_set_updated_at();

-- 3. Función Trigger para evaluación autónoma de alertas de stock mínimo
-- Justificación de eficiencia computacional:
-- Se ejecuta de forma nativa en el motor PostgreSQL cada vez que el stock disminuye
-- (por ventas, mermas o despachos), evitando consultas repetitivas de sondeo (polling)
-- desde el backend Spring Boot y garantizando atomicidad transaccional ACID.
CREATE OR REPLACE FUNCTION fn_evaluar_stock_alerta()
RETURNS TRIGGER AS $$
BEGIN
    -- Se dispara únicamente cuando el stock cae a o por debajo del stock mínimo
    -- y previamente se encontraba por encima, o al inicializarlo bajo el umbral.
    IF (NEW.stock_actual <= NEW.stock_minimo) AND (OLD.stock_actual > OLD.stock_minimo OR OLD.stock_actual IS NULL) THEN
        INSERT INTO evento_sistema (
            id,
            tipo,
            descripcion,
            usuario_id,
            fecha
        ) VALUES (
            gen_random_uuid(),
            'ALERTA_STOCK',
            'Alerta automática de inventario: El producto "' || NEW.nombre || '" (código: ' || NEW.codigo_tecnico ||
            ') ha alcanzado nivel crítico de inventario. Stock actual: ' || NEW.stock_actual ||
            ' unidades, Stock mínimo: ' || NEW.stock_minimo || ' unidades.',
            NULL,
            CURRENT_TIMESTAMP
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 4. Trigger de inventario sobre producto
DROP TRIGGER IF EXISTS trg_producto_alerta_stock ON producto;
CREATE TRIGGER trg_producto_alerta_stock
AFTER UPDATE OF stock_actual ON producto
FOR EACH ROW
EXECUTE FUNCTION fn_evaluar_stock_alerta();

-- 5. Stored Procedure para consolidación de cierre de caja en base de datos
-- Permite que el cálculo agregado de ventas por canal y totales del día
-- se ejecute en el motor relacional en O(N) índices sin sobrecargar la memoria de la JVM.
CREATE OR REPLACE PROCEDURE sp_calcular_cierre_caja(
    IN  p_fecha DATE,
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
    WHERE DATE(v.fecha) = p_fecha
      AND v.estado = 'COMPLETADA';
END;
$$;
