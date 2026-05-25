package com.gelox.backend.ventas.rural;

import com.gelox.backend.entities.ClienteRural;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * RF33 — Repositorio de clientes rurales.
 */
public interface ClienteRuralRepository extends JpaRepository<ClienteRural, UUID> {

    /** Busca por nombre o teléfono (case-insensitive) para el query param ?q=. */
    @Query("SELECT c FROM ClienteRural c WHERE " +
           "LOWER(c.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(c.telefono) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<ClienteRural> buscarPorNombreOTelefono(@Param("q") String q);

    /** Verifica si ya existe un cliente con el mismo teléfono (dedup). */
    Optional<ClienteRural> findByTelefono(String telefono);

    /** Retorna todos los clientes ordenados por nombre. */
    List<ClienteRural> findAllByOrderByNombreAsc();
}
