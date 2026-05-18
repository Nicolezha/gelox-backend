package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evento_sistema")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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
        if (fecha == null) fecha = LocalDateTime.now();
    }
}
