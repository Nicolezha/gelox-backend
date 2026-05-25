package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * RF33 — Cliente rural recurrente.
 * No tiene columna 'correo' en BD (el RF lo menciona pero no existe).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cliente_rural")
public class ClienteRural {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    @Column
    private String telefono;

    @Column(columnDefinition = "TEXT")
    private String direccion;

    @Column
    private String corregimiento;

    /** true cuando se registra explícitamente como cliente recurrente. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean recurrente = false;
}
