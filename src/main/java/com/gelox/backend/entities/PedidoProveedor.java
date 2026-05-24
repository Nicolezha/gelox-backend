package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representa un pedido generado al proveedor (Nutresa).
 * RF21 — estado inicial PENDIENTE; pasa a RECIBIDO en RF23.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pedido_proveedor")
public class PedidoProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    /**
     * Mapeado con @ColumnTransformer para forzar el cast al tipo PostgreSQL
     * personalizado {@code estado_pedido} al insertar/actualizar.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, columnDefinition = "estado_pedido")
    @org.hibernate.annotations.ColumnTransformer(write = "?::estado_pedido")
    @Builder.Default
    private EstadoPedido estado = EstadoPedido.PENDIENTE;

    @Column(name = "notas", columnDefinition = "TEXT")
    private String notas;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Relación bidireccional para poder navegar los ítems desde el pedido. */
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemPedidoProveedor> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (fecha == null)     fecha     = LocalDate.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
