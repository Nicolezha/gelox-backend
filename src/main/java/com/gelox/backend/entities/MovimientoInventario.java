package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Registro de cada movimiento de stock (RF23 ENTRADA, RF25 PERDIDA, etc.).
 * Funciona como bitácora de inventario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Cast explícito al tipo PostgreSQL {@code tipo_movimiento} al escribir.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, columnDefinition = "tipo_movimiento")
    @org.hibernate.annotations.ColumnTransformer(write = "?::tipo_movimiento")
    private TipoMovimiento tipo;

    /** Siempre positivo; el signo lo da el tipo de movimiento. */
    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    /** Stock del producto inmediatamente después del movimiento. */
    @Column(name = "stock_resultante", nullable = false)
    private Integer stockResultante;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void onCreate() {
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
