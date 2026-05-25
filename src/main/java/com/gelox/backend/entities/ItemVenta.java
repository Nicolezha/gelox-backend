package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_venta")
public class ItemVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Unidades individuales vendidas en ventanilla. */
    @Column(name = "cantidad_unidades", nullable = false)
    private Integer cantidadUnidades;

    /** Cajas completas (despacho rural/comerciante). 0 para ventanilla. */
    @Column(name = "cantidad_cajas", nullable = false)
    @Builder.Default
    private Integer cantidadCajas = 0;

    /** Precio vigente en el momento de confirmar la venta. */
    @Column(name = "precio_unitario", nullable = false, precision = 15, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "subtotal", nullable = false, precision = 15, scale = 2)
    private BigDecimal subtotal;
}
