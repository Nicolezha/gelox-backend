package com.gelox.backend.gobierno.dto;

import com.gelox.backend.gobierno.CuotaService.UsoCuota;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF69 — Dashboard del Superadministrador (contrato del mock de T193,
 * {@code services/mocks/mockPlataforma.js}).
 */
public record PanelPlataformaDTO(
        LocalDate desde,
        LocalDate hasta,
        LocalDateTime generadoEn,
        Distribuidoras distribuidoras,
        Usuarios usuarios,
        ValorConteo ventas,
        PedidosProveedor pedidosProveedor,
        long planillasLiquidadas,
        Almacenamiento almacenamiento,
        Ia ia,
        List<PorDistribuidora> porDistribuidora,
        List<PuntoSerie> serie
) {

    public record Distribuidoras(long total, Map<String, Long> porEstado) {}

    public record Usuarios(long registrados, long habilitados, long nuevos) {}

    public record ValorConteo(BigDecimal valor, long transacciones) {}

    /** pedidosProveedor usa "cantidad" en vez de "transacciones" (contrato del mock). */
    public record PedidosProveedor(long cantidad, BigDecimal valor) {}

    public record Almacenamiento(BigDecimal usadoMb, long asignadoMb) {}

    public record Ia(long llamadas, long voz, long recomendaciones) {}

    public record PorDistribuidora(
            UUID distribuidoraId,
            String nombreComercial,
            String razonSocial,
            UUID planId,
            String planNombre,
            String estado,
            ValorConteo ventas,
            long llamadasIa,
            Map<String, UsoCuota> uso
    ) {}

    public record PuntoSerie(LocalDate fecha, BigDecimal ventas, long transacciones, long llamadasIa, long usuariosNuevos) {}
}
