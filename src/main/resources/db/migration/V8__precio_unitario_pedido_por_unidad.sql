-- La inversión de los reportes es SUM(precio_unitario × cantidad_recibida) con cantidad_recibida en
-- unidades, así que precio_unitario debe ser el costo POR UNIDAD (así lo guarda registrarEntrada desde
-- el commit cd8ee9c). Las entradas registradas antes guardaron el costo POR CAJA (= producto.precio_costo)
-- e inflaban la inversión por unidades_por_caja. Se convierten a costo por unidad.
-- Solo toca ítems ya recibidos cuyo precio coincide con el de la caja; es idempotente.
UPDATE item_pedido_proveedor ipp
   SET precio_unitario = ROUND(ipp.precio_unitario / p.unidades_por_caja, 2)
  FROM producto p
 WHERE p.id = ipp.producto_id
   AND p.unidades_por_caja > 1
   AND ipp.cantidad_recibida > 0
   AND ipp.precio_unitario = p.precio_costo;
