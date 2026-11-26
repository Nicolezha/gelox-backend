package com.gelox.backend.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CrearProductoRequest {

    @NotBlank(message = "El código técnico es obligatorio")
    private String codigoTecnico;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    /** Nombre de una categoría de la tabla; alternativa a categoriaId (una de las dos es obligatoria). */
    private String categoria;

    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0", inclusive = false, message = "El precio de venta debe ser mayor a 0")
    private BigDecimal precioVenta;

    @DecimalMin(value = "0", message = "El precio de costo debe ser >= 0")
    private BigDecimal precioCosto;

    private String descripcion;

    @Min(value = 0, message = "El stock mínimo debe ser >= 0")
    private Integer stockMinimo;

    @Min(value = 0, message = "El stock medio debe ser >= 0")
    private Integer stockMedio;

    private String imagenUrl;

    private String unidadMedida;

    private Integer unidadesPorCaja;

    private BigDecimal precioComerciente;


    // RF63/RF64
    private String sku;

    private String marca;

    private java.util.UUID proveedorId;

    private java.util.UUID categoriaId;

    private java.util.UUID unidadBaseId;

    @jakarta.validation.Valid
    private java.util.List<FactorConversionRequest> factores;

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

    public Integer getStockMedio() { return stockMedio; }
    public void setStockMedio(Integer stockMedio) { this.stockMedio = stockMedio; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }

    public Integer getUnidadesPorCaja() { return unidadesPorCaja; }
    public void setUnidadesPorCaja(Integer unidadesPorCaja) { this.unidadesPorCaja = unidadesPorCaja; }

    public java.math.BigDecimal getPrecioComerciente() { return precioComerciente; }
    public void setPrecioComerciente(java.math.BigDecimal precioComerciente) { this.precioComerciente = precioComerciente; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getMarca() { return marca; }
    public void setMarca(String marca) { this.marca = marca; }

    public java.util.UUID getProveedorId() { return proveedorId; }
    public void setProveedorId(java.util.UUID proveedorId) { this.proveedorId = proveedorId; }

    public java.util.UUID getCategoriaId() { return categoriaId; }
    public void setCategoriaId(java.util.UUID categoriaId) { this.categoriaId = categoriaId; }

    public java.util.UUID getUnidadBaseId() { return unidadBaseId; }
    public void setUnidadBaseId(java.util.UUID unidadBaseId) { this.unidadBaseId = unidadBaseId; }

    public java.util.List<FactorConversionRequest> getFactores() { return factores; }
    public void setFactores(java.util.List<FactorConversionRequest> factores) { this.factores = factores; }
}
