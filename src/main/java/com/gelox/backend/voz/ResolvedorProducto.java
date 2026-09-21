package com.gelox.backend.voz;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Resuelve un fragmento dicho a viva voz ("festival", "solo lac") contra el
 * catálogo de productos activos, tolerando errores de pronunciación mediante
 * distancia de Levenshtein. No usa ningún servicio externo.
 * <p>
 * Si el segundo candidato queda a menos de 0.10 del primero, el resultado es
 * ambiguo y quien use esta lista debe preguntar cuál de los dos quiso decir
 * el usuario en lugar de asumir el primero.
 */
@Component
@RequiredArgsConstructor
public class ResolvedorProducto {

    private static final double PUNTAJE_MINIMO = 0.75;

    private final ProductoRepository productoRepository;

    public record ProductoCandidato(UUID id, String nombre, double score, Integer unidadesPorCaja) {
    }

    public List<ProductoCandidato> resolver(String fragmento) {
        String fragmentoNormalizado = NormalizadorVoz.normalizar(fragmento);
        if (fragmentoNormalizado.isEmpty()) return List.of();

        return productoRepository.findByActivoTrue().stream()
                .map(producto -> new ProductoCandidato(
                        producto.getId(),
                        producto.getNombre(),
                        puntaje(fragmentoNormalizado, producto),
                        producto.getUnidadesPorCaja()))
                .filter(candidato -> candidato.score() >= PUNTAJE_MINIMO)
                .sorted(Comparator.comparingDouble(ProductoCandidato::score).reversed())
                .toList();
    }

    private double puntaje(String fragmentoNormalizado, Producto producto) {
        String nombreNormalizado = NormalizadorVoz.normalizar(producto.getNombre());

        if (nombreNormalizado.contains(fragmentoNormalizado)) return 1.0;

        int distancia = distanciaLevenshtein(fragmentoNormalizado, nombreNormalizado);
        int largoMayor = Math.max(fragmentoNormalizado.length(), nombreNormalizado.length());
        return largoMayor == 0 ? 0.0 : 1.0 - (double) distancia / largoMayor;
    }

    private static int distanciaLevenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + costo);
            }
        }
        return dp[a.length()][b.length()];
    }
}
