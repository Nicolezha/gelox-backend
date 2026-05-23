package com.gelox.backend.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * Todos los campos son opcionales (PATCH semántico sobre PUT).
 * Solo se actualiza el campo si el valor recibido no es null.
 */
public class EditarProductoRequest {

    private String codigoTecnico;

    private String nombre;

    private String categoria;   // PALETAS | CONOS | FAMILIARES

    @DecimalMin(value = "0", message = "El precio de venta debe ser >= 0")
    private BigDecimal precioVenta;

    @DecimalMin(value = "0", message = "El precio de costo debe ser >= 0")
    private BigDecimal precioCosto;

    private String descripcion;

    @Min(value = 0, message = "El stock mínimo debe ser >= 0")
    private Integer stockMinimo;

    private String imagenUrl;

    // Getters y Setters

    public String getCodigoTecnico() { return codigoTecnico; }
    public void setCodigoTecnico(String codigoTecnico) { this.codigoTecnico = codigoTecnico; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }

    public BigDecimal getPrecioCosto() { return precioCosto; }
    public void setPrecioCosto(BigDecimal precioCosto) { this.precioCosto = precioCosto; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }
}
