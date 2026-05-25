package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF32 — Pedido rural (1:1 con Venta de canal RURAL).
 * datos_destinatario se persiste como JSON string para preservar los datos
 * incluso si el cliente_rural es eliminado en el futuro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pedido_rural")
public class PedidoRural {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false, unique = true)
    private Venta venta;

    @Column(name = "costo_envio", nullable = false)
    @Builder.Default
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    /** Estado del envío: PENDIENTE, ENVIADO, ENTREGADO. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_envio", nullable = false)
    @Builder.Default
    private EstadoEnvio estadoEnvio = EstadoEnvio.PENDIENTE;

    /**
     * JSON string con nombre, teléfono, dirección y corregimiento del destinatario.
     * Se guarda como texto para no perder los datos si el cliente_rural se elimina.
     */
    @Column(name = "datos_destinatario", columnDefinition = "TEXT")
    private String datosDestinatario;
}
