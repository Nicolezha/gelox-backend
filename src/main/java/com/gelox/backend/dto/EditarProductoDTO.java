package com.gelox.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EditarProductoDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0")
    private BigDecimal precioVenta;

    @NotNull(message = "El precio de costo es obligatorio")
    @DecimalMin(value = "0.0", message = "El precio de costo no puede ser negativo")
    private BigDecimal precioCosto;

    @NotNull(message = "La categoría es obligatoria")
    private String categoria;

    @Min(value = 0, message = "El stock mínimo no puede ser negativo")
    private Integer stockMinimo;

    private String imagenUrl;

    private String descripcion;

    private Boolean activo;
}