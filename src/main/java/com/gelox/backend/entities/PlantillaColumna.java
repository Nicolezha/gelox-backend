package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * RF67 — columna de una plantilla GENERAR_FILAS. No lleva tenant_id: hereda el de su plantilla,
 * como los item_* de su padre (FP-15 §2.14).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plantilla_columna")
public class PlantillaColumna {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private PlantillaExportacion plantilla;

    @Column(nullable = false)
    private Integer orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampoPlantilla campo;

    @Column(nullable = false, length = 80)
    private String encabezado;

    @Column(name = "valor_fijo", length = 120)
    private String valorFijo;
}
