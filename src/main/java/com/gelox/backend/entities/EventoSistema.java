package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "evento_sistema")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoSistema implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoEvento tipo;

    @Column(nullable = false)
    private String descripcion;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @PrePersist
    public void prePersist() {
        if (fecha == null) fecha = LocalDateTime.now(ZoneOffset.UTC);
    }
}
