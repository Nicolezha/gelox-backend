package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Línea de un pedido al proveedor.
 * cantidad_recibida se actualiza en RF23 al registrar la entrada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_pedido_proveedor")
public class ItemPedidoProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoProveedor pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /** Unidades solicitadas al proveedor (RF21). */
    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada;

    /** Unidades efectivamente recibidas (se llena en RF23). */
    @Builder.Default
    @Column(name = "cantidad_recibida", nullable = false)
    private Integer cantidadRecibida = 0;

    /** Precio negociado con el proveedor; se actualiza en RF23. */
    @Builder.Default
    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;
}
