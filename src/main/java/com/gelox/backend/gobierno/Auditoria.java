package com.gelox.backend.gobierno;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * RF71 — Fila del log de auditoría (FP-15 §2.14.1). No es TenantAware: el Superadministrador
 * la consulta entre distribuidoras y sus propias operaciones no tienen tenant (tenant_id null).
 */
@Entity
@Table(name = "auditoria")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Auditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "fecha_hora", nullable = false, updatable = false)
    private LocalDateTime fechaHora;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "rol", length = 30)
    private String rol;

    @Column(name = "accion", nullable = false, length = 60)
    private String accion;

    @Column(name = "entidad", length = 60)
    private String entidad;

    @Column(name = "entidad_id")
    private UUID entidadId;

    /** JSON en texto. */
    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @PrePersist
    protected void onCreate() {
        if (fechaHora == null) fechaHora = LocalDateTime.now(ZoneId.of("America/Bogota"));
    }
}
