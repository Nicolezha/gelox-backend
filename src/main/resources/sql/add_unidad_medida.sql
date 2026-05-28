-- Migración: agrega columna unidad_medida a la tabla productos
-- Ejecutar en el SQL Editor de Supabase (o directamente en la BD)

ALTER TABLE producto
    ADD COLUMN IF NOT EXISTS unidad_medida VARCHAR(50) DEFAULT 'Unidades';
