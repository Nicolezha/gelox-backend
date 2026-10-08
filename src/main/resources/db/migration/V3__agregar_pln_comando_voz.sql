ALTER TABLE comando_voz ADD COLUMN IF NOT EXISTS probabilidad_intencion NUMERIC(4,3);
ALTER TABLE comando_voz ADD COLUMN IF NOT EXISTS metodo_clasificacion VARCHAR(10);
