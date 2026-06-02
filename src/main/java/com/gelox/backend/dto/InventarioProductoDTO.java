package com.gelox.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventarioProductoDTO {

    private String id;
    private String codigoTecnico;
    private String nombre;
    private Integer cantidadDisponible;
    private BigDecimal precio;
    private String estado;
    private BigDecimal precioCosto;        // null si el rol no es ADMINISTRADOR
    private BigDecimal precioComerciente;  // precio diferenciado para planillas

    public InventarioProductoDTO() {}

    public InventarioProductoDTO(String id, String codigoTecnico, String nombre,
                                  Integer cantidadDisponible, BigDecimal precio,
                                  String estado, BigDecimal precioCosto,
                                  BigDecimal precioComerciente) {
        this.id = id;
        this.codigoTecnico = codigoTecnico;
        this.nombre = nombre;
        this.cantidadDisponible = cantidadDisponible;
        this.precio = precio;
        this.estado = estado;
        this.precioCosto = precioCosto;
        this.precioComerciente = precioComerciente;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCodigoTecnico() { return codigoTecnico; }
    public void setCodigoTecnico(String codigoTecnico) { this.codigoTecnico = codigoTecnico; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public Integer getCantidadDisponible() { return cantidadDisponible; }
    public void setCantidadDisponible(Integer cantidadDisponible) { this.cantidadDisponible = cantidadDisponible; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public BigDecimal getPrecioCosto() { return precioCosto; }
    public void setPrecioCosto(BigDecimal precioCosto) { this.precioCosto = precioCosto; }

    public BigDecimal getPrecioComerciente() { return precioComerciente; }
    public void setPrecioComerciente(BigDecimal precioComerciente) { this.precioComerciente = precioComerciente; }
}
