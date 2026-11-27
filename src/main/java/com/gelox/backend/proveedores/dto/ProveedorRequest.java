package com.gelox.backend.proveedores.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * RF63 — cuerpo de POST /api/proveedores y PUT /api/proveedores/{id}.
 * Tiempo de entrega y monto mínimo son obligatorios y ≥ 0. En la edición, días hábiles null
 * conserva su valor.
 */
public record ProveedorRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String nombre,

        @Size(max = 20, message = "El NIT no puede superar 20 caracteres")
        String nit,

        @Size(max = 120, message = "El contacto no puede superar 120 caracteres")
        String contactoNombre,

        @Size(max = 20, message = "El teléfono no puede superar 20 caracteres")
        String telefono,

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String correo,

        @NotNull(message = "El tiempo de entrega es obligatorio")
        @Min(value = 0, message = "El tiempo de entrega no puede ser negativo")
        Integer tiempoEntregaDias,

        Boolean diasHabiles,

        @NotNull(message = "El monto mínimo de pedido es obligatorio")
        @DecimalMin(value = "0", message = "El monto mínimo de pedido no puede ser negativo")
        BigDecimal montoMinimoPedido
) {}
