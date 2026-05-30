package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Comerciante independiente que trabaja con Gelox (RF34, RF35).
 * Mapea la tabla {@code comerciante}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comerciante")
public class Comerciante {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "municipio", length = 100)
    private String municipio;

    @Column(name = "direccion", columnDefinition = "TEXT")
    private String direccion;

    @Column(name = "telefono", length = 50)
    private String telefono;

    /** Nombre del contacto de emergencia. Reutiliza la columna {@code contacto_emergencia}. */
    @Column(name = "contacto_emergencia", length = 255)
    private String contactoEmergenciaNombre;

    /** Parentesco del contacto de emergencia (nueva columna). */
    @Column(name = "contacto_emergencia_parentesco", length = 100)
    private String contactoEmergenciaParentesco;

    /** Talla de uniforme (XS, S, M, L, XL, XXL, …). */
    @Column(name = "talla_uniforme", length = 10)
    private String tallaUniforme;

    /** Placa del carrito de helados. */
    @Column(name = "placa", length = 20)
    private String placa;

    /** URL pública de la fotografía de perfil. */
    @Column(name = "foto_url", columnDefinition = "TEXT")
    private String fotoUrl;

    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
