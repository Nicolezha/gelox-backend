package com.gelox.backend.gobierno;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface ArchivoAlmacenadoRepository extends JpaRepository<ArchivoAlmacenado, UUID> {

    /** Bytes subidos por la distribuidora (0 si no ha subido nada). */
    @Query("select coalesce(sum(a.bytes), 0) from ArchivoAlmacenado a where a.tenantId = :tenantId")
    long sumarBytes(@Param("tenantId") UUID tenantId);

    /** Fila de un archivo borrado del almacenamiento. */
    @Transactional
    void deleteByUrl(String url);
}
