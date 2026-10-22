package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Distribuidora (tenant) del sistema multi-tenant.
 * Magico Sabor es el tenant piloto (id 00000000-0000-0000-0000-000000000001).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "distribuidora")
public class Distribuidora {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    /** Transiciones permitidas del ciclo de vida (FP-15, sección 2.12.2). */
    private static final Map<EstadoDistribuidora, Set<EstadoDistribuidora>> TRANSICIONES =
            new EnumMap<>(EstadoDistribuidora.class);

    static {
        TRANSICIONES.put(EstadoDistribuidora.PRUEBA,
                EnumSet.of(EstadoDistribuidora.ACTIVO, EstadoDistribuidora.SUSPENDIDO, EstadoDistribuidora.INACTIVO));
        TRANSICIONES.put(EstadoDistribuidora.ACTIVO,
                EnumSet.of(EstadoDistribuidora.SUSPENDIDO, EstadoDistribuidora.INACTIVO));
        TRANSICIONES.put(EstadoDistribuidora.SUSPENDIDO,
                EnumSet.of(EstadoDistribuidora.ACTIVO, EstadoDistribuidora.INACTIVO));
        // INACTIVO es estado final: cualquier cambio responde 409 "Transición no válida"
        TRANSICIONES.put(EstadoDistribuidora.INACTIVO, EnumSet.noneOf(EstadoDistribuidora.class));
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "nit", nullable = false, unique = true, length = 20)
    private String nit;

    @Column(name = "nombre_comercial", nullable = false, length = 100)
    private String nombreComercial;

    @Column(name = "subdominio", nullable = false, unique = true, length = 30)
    private String subdominio;

    @Column(name = "correo_gerente", nullable = false, length = 150)
    private String correoGerente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gerente_titular_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario gerenteTitular;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "direccion", columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "barrio", length = 80)
    private String barrio;

    @Column(name = "ciudad", length = 80)
    private String ciudad;

    @Column(name = "plan", nullable = false, length = 30)
    private String plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoDistribuidora estado = EstadoDistribuidora.ACTIVO;

    @Column(name = "motivo_estado", columnDefinition = "TEXT")
    private String motivoEstado;

    @Column(name = "fecha_estado", nullable = false)
    private LocalDateTime fechaEstado;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "favicon_url", columnDefinition = "TEXT")
    private String faviconUrl;

    @Column(name = "color_primario", nullable = false, length = 7)
    private String colorPrimario;

    @Column(name = "color_secundario", nullable = false, length = 7)
    private String colorSecundario;

    @Column(name = "eslogan", length = 150)
    private String eslogan;

    @Column(name = "whatsapp", length = 20)
    private String whatsapp;

    @Column(name = "horario", length = 100)
    private String horario;

    @Column(name = "maps_url", columnDefinition = "TEXT")
    private String mapsUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZONA_BOGOTA);
        this.createdAt = now;
        this.updatedAt = now;
        if (fechaEstado == null) fechaEstado = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZONA_BOGOTA);
    }

    /** Solo las distribuidoras en PRUEBA o ACTIVO pueden operar. */
    public boolean permiteAcceso() {
        return estado == EstadoDistribuidora.PRUEBA || estado == EstadoDistribuidora.ACTIVO;
    }

    public boolean puedeCambiarA(EstadoDistribuidora destino) {
        return estado != null && destino != null
                && TRANSICIONES.getOrDefault(estado, Set.of()).contains(destino);
    }
}
