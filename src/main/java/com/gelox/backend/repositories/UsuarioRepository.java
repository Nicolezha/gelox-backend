package com.gelox.backend.repositories;

import com.gelox.backend.entities.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByFirebaseUid(String firebaseUid);

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    List<Usuario> findByActivoTrue();

    /** RF62 — id del gerente titular de la distribuidora (distribuidora.gerente_titular_id). */
    @Query("select d.gerenteTitular.id from Distribuidora d where d.id = :tenantId")
    Optional<UUID> findGerenteTitularId(@Param("tenantId") UUID tenantId);
}
