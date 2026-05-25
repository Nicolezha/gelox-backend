package com.gelox.backend.ventas.rural.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RF32 — Request para confirmar un pedido rural completo.
 *
 * Se puede usar con cliente existente (clienteRuralId) o con datos ad-hoc
 * (nombreDestinatario, telefonoDestinatario, etc.).
 * Si clienteRuralId es null, nombreDestinatario debe estar presente
 * (validado en el service).
 */
public record ConfirmarPedidoRuralRequest(

        UUID   clienteRuralId,         // opcional: cliente previamente registrado

        String nombreDestinatario,     // requerido si clienteRuralId es null
        String telefonoDestinatario,   // opcional
        String direccionDestinatario,  // opcional
        String corregimiento,          // opcional

        @DecimalMin(value = "0", message = "El costo de envío no puede ser negativo")
        BigDecimal costoEnvio,         // default 0

        @NotEmpty(message = "El pedido debe tener al menos un ítem")
        @Valid
        List<ItemPedidoRuralRequest> items
) {
    public BigDecimal costoEnvioEfectivo() {
        return costoEnvio != null ? costoEnvio : BigDecimal.ZERO;
    }
}
