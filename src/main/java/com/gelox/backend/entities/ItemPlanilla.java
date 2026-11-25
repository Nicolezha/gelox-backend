package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_planilla")
public class ItemPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planilla_id", nullable = false)
    private PlanillaComerciante planilla;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "unidades_despachadas", nullable = false)
    private Integer unidadesDespachadas;

    @Builder.Default
    @Column(name = "unidades_devueltas", nullable = false)
    private Integer unidadesDevueltas = 0;

    @Column(name = "precio_venta", nullable = false, precision = 15, scale = 2)
    private BigDecimal precioVenta;

    // Postgres GENERATED column — never set from JPA
    @Column(name = "ganancia", insertable = false, updatable = false)
    private BigDecimal ganancia;

    /** RF65 — copia del precio al público al momento del despacho (null si no se indicó). */
    @Column(name = "precio_publico", precision = 12, scale = 2)
    private BigDecimal precioPublico;

    /** RF66 — lo que le corresponde al comerciante por este ítem, calculado al liquidar. */
    @Column(name = "ganancia_comerciante", precision = 12, scale = 2)
    private BigDecimal gananciaComerciante;

    /** Un precio al público nulo o en 0 significa que el producto no lo tiene (FP-15 §2.17.2). */
    public boolean tienePrecioPublico() {
        return precioPublico != null && precioPublico.signum() > 0;
    }

    /** El precio al público o, si el producto no lo tiene, el precio al comerciante. */
    public BigDecimal precioPublicoEfectivo() {
        return tienePrecioPublico() ? precioPublico : precioVenta;
    }
}
