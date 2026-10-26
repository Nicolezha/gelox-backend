-- Ajustes posteriores a V5 (que ya estaba aplicada y no debe editarse).

-- cierre_caja no tiene índice compuesto propio: necesita el índice simple por tenant.
CREATE INDEX IF NOT EXISTS idx_cierre_caja_tenant ON cierre_caja (tenant_id);

-- Gerente titular de Mágico Sabor: el usuario ADMINISTRADOR de Ayde Arévalo.
-- Si ese usuario no existe en la base donde corre la migración, el UPDATE no afecta filas (no falla).
-- Toma también su correo como correo_gerente, en lugar del valor piloto del INSERT.
UPDATE distribuidora d
   SET gerente_titular_id = u.id,
       correo_gerente     = u.correo
  FROM usuario u
 WHERE d.id = '00000000-0000-0000-0000-000000000001'
   AND u.id = '44e3104d-3483-458d-9898-f3afa216e580'
   AND u.rol = 'ADMINISTRADOR';
