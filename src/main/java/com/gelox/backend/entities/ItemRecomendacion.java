package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Linea de una {@link RecomendacionCompra}: producto, cantidad sugerida y decision tomada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "item_recomendacion")
public class ItemRecomendacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recomendacion_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RecomendacionCompra recomendacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "prioridad", nullable = false)
    private Integer prioridad;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgencia", nullable = false, length = 10)
    private UrgenciaRecomendacion urgencia;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "demanda_diaria", nullable = false, precision = 10, scale = 3)
    private BigDecimal demandaDiaria;

    @Column(name = "factor_estacionalidad", nullable = false, precision = 4, scale = 2)
    private BigDecimal factorEstacionalidad;

    @Column(name = "stock_seguridad", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockSeguridad;

    @Column(name = "punto_reorden", nullable = false, precision = 10, scale = 2)
    private BigDecimal puntoReorden;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual;

    @Column(name = "cobertura_dias", precision = 8, scale = 2)
    private BigDecimal coberturaDias;

    @Column(name = "unidades_sugeridas", nullable = false)
    private Integer unidadesSugeridas;

    @Column(name = "cajas_sugeridas", nullable = false)
    private Integer cajasSugeridas;

    @Enumerated(EnumType.STRING)
    @Column(name = "ajuste_presupuesto", nullable = false, length = 10)
    private AjustePresupuesto ajustePresupuesto;

    @Column(name = "costo_estimado", nullable = false, precision = 15, scale = 2)
    private BigDecimal costoEstimado;

    @Column(name = "margen_esperado", nullable = false, precision = 15, scale = 2)
    private BigDecimal margenEsperado;

    @Column(name = "justificacion", nullable = false, columnDefinition = "TEXT")
    private String justificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 12)
    @Builder.Default
    private DecisionItem decision = DecisionItem.PENDIENTE;

    @Column(name = "cajas_aprobadas")
    private Integer cajasAprobadas;

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @Column(name = "fecha_decision")
    private LocalDateTime fechaDecision;
}
