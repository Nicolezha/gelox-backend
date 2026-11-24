package com.gelox.backend.gobierno;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * RF70 — Archivo subido a Supabase Storage por una distribuidora. Sirve para medir su
 * almacenamiento: las carpetas del bucket son por tipo (productos, comerciantes, usuarios),
 * no por distribuidora. El tenant se fija explícitamente al guardar.
 */
@Entity
@Table(name = "archivo_almacenado")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchivoAlmacenado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "carpeta", nullable = false, length = 120)
    private String carpeta;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "bytes", nullable = false)
    private Long bytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneId.of("America/Bogota"));
    }
}
