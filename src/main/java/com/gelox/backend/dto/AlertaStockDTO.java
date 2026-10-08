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
    /** RF24 — siempre "BAJO_STOCK" (solo se alertan productos con stock ≤ mínimo). */
    private String estado;
    /** RF24 — severidad: URGENTE (stock 0) | ALTA (≤ 50 % del mínimo) | MEDIA (≤ mínimo). */
    private String nivel;

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
        this.estado = "BAJO_STOCK";
        this.nivel = calcularNivel(stockActual, stockMinimo);
    }

    /** Severidad según el stock mínimo configurado del producto. */
    public static String calcularNivel(Integer stockActual, Integer stockMinimo) {
        int actual = stockActual != null ? stockActual : 0;
        int minimo = stockMinimo != null ? stockMinimo : 0;
        if (actual <= 0)              return "URGENTE";
        if (actual * 2 <= minimo)     return "ALTA";
        return "MEDIA";
    }

    /** Orden de severidad: menor = más urgente. */
    public static int pesoNivel(String nivel) {
        return switch (nivel) {
            case "URGENTE" -> 0;
            case "ALTA"    -> 1;
            default        -> 2;
        };
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

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
}
