package com.gelox.backend.gobierno;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * RF70 — Plan de suscripción (FP-15 §2.14.1). La distribuidora lo referencia por
 * {@code distribuidora.plan_id}. Un máximo null significa "sin límite".
 */
@Entity
@Table(name = "plan_suscripcion")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSuscripcion {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private String nombre;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "precio_mensual", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioMensual = BigDecimal.ZERO;

    @Column(name = "max_usuarios")
    private Integer maxUsuarios;

    @Column(name = "max_productos")
    private Integer maxProductos;

    @Column(name = "max_comerciantes")
    private Integer maxComerciantes;

    @Column(name = "max_almacenamiento_mb")
    private Integer maxAlmacenamientoMb;

    @Column(name = "max_llamadas_ia_mes")
    private Integer maxLlamadasIaMes;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZONA_BOGOTA);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZONA_BOGOTA);
    }

    /** Máximo del plan para la cuota (en MB para ALMACENAMIENTO_MB), o null si no tiene límite. */
    public Integer limite(TipoCuota tipo) {
        return switch (tipo) {
            case USUARIOS -> maxUsuarios;
            case PRODUCTOS -> maxProductos;
            case COMERCIANTES -> maxComerciantes;
            case ALMACENAMIENTO_MB -> maxAlmacenamientoMb;
            case LLAMADAS_IA_MES -> maxLlamadasIaMes;
        };
    }
}
