package com.gelox.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertaStockDTO {

    private String id;
    private String codigoTecnico;
    private String nombre;
    private String categoria;
    private Integer stockActual;
    private Integer stockMinimo;
    private Integer stockMedio;

    public AlertaStockDTO() {}

    public AlertaStockDTO(String id, String codigoTecnico, String nombre,
                          String categoria, Integer stockActual, Integer stockMinimo, Integer stockMedio) {
        this.id = id;
        this.codigoTecnico = codigoTecnico;
        this.nombre = nombre;
        this.categoria = categoria;
        this.stockActual = stockActual;
        this.stockMinimo = stockMinimo;
        this.stockMedio = stockMedio;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCodigoTecnico() { return codigoTecnico; }
    public void setCodigoTecnico(String codigoTecnico) { this.codigoTecnico = codigoTecnico; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Integer getStockActual() { return stockActual; }
    public void setStockActual(Integer stockActual) { this.stockActual = stockActual; }

    public Integer getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(Integer stockMinimo) { this.stockMinimo = stockMinimo; }

    public Integer getStockMedio() { return stockMedio; }
    public void setStockMedio(Integer stockMedio) { this.stockMedio = stockMedio; }
}
