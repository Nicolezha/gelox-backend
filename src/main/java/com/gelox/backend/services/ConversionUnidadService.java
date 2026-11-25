package com.gelox.backend.services;

import com.gelox.backend.entities.FactorConversion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.FactorConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * RF64 / FP-15 §1.6.1 — pasa cantidades de una unidad cualquiera a la unidad base del producto,
 * que es en la que se lleva el stock.
 * <ul>
 *   <li>CJ → base: {@code unidades_por_caja} del producto (como siempre);</li>
 *   <li>cualquier otra unidad: {@code factor_conversion} del producto hacia su unidad base.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversionUnidadService {

    private static final String UNIDAD_CAJA = "CJ";

    private final FactorConversionRepository factorRepository;

    /**
     * Unidades base que suman {@code cantidad} de la unidad {@code codigo}. Sin unidad ni cantidad
     * (el caso de siempre: solo cajas y unidades) devuelve 0; con solo una de las dos, 400.
     */
    public int aUnidadBase(Producto producto, String codigo, Integer cantidad) {
        boolean sinUnidad = codigo == null || codigo.isBlank();
        if (sinUnidad && cantidad == null) {
            return 0;
        }
        if (sinUnidad || cantidad == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Para otra unidad hay que enviar la unidad y su cantidad");
        }
        if (cantidad < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad no puede ser negativa");
        }

        String unidad = codigo.trim();
        String base = producto.getUnidadBase() != null ? producto.getUnidadBase().getCodigo() : null;
        if (unidad.equalsIgnoreCase(base)) {
            return cantidad;
        }
        if (UNIDAD_CAJA.equalsIgnoreCase(unidad)) {
            Integer porCaja = producto.getUnidadesPorCaja();
            if (porCaja == null || porCaja <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("El producto '%s' no tiene unidades por caja", producto.getNombre()));
            }
            return cantidad * porCaja;
        }
        if (producto.getUnidadBase() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("El producto '%s' no tiene unidad base", producto.getNombre()));
        }

        FactorConversion factor = factorRepository
                .findHaciaBase(producto.getId(), unidad, producto.getUnidadBase().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("El producto '%s' no tiene factor de conversión de %s a %s",
                                producto.getNombre(), unidad.toUpperCase(), base)));

        BigDecimal enBase = factor.getFactor().multiply(BigDecimal.valueOf(cantidad));
        if (enBase.stripTrailingZeros().scale() > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("%d %s no equivalen a un número entero de %s en '%s'",
                            cantidad, unidad.toUpperCase(), base, producto.getNombre()));
        }
        return enBase.intValueExact();
    }
}
