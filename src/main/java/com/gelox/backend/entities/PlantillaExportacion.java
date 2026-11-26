package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** RF67 — plantilla de exportación de pedidos de un proveedor (tabla plantilla_exportacion, V10). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "plantilla_exportacion")
public class PlantillaExportacion implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModoPlantilla modo = ModoPlantilla.RELLENAR_CATALOGO;

    /** Nulo = usa templates/catalogo.xlsx (el catálogo de Nutresa). */
    @Column(name = "archivo_base_url", columnDefinition = "TEXT")
    private String archivoBaseUrl;

    @Column(name = "fila_encabezado", nullable = false)
    private Integer filaEncabezado = 1;

    @Column(name = "encabezado_codigo", length = 80)
    private String encabezadoCodigo;

    @Column(name = "encabezado_cantidad", length = 80)
    private String encabezadoCantidad;

    @Column(name = "encabezado_unidad", length = 80)
    private String encabezadoUnidad;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plantilla", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<PlantillaColumna> columnas = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        updatedAt = LocalDateTime.now();
    }
}
