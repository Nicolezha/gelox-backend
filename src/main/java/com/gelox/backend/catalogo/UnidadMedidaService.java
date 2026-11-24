package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.UnidadMedidaDTO;
import com.gelox.backend.catalogo.dto.UnidadMedidaRequest;
import com.gelox.backend.entities.UnidadMedida;
import com.gelox.backend.repositories.UnidadMedidaRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** RF64 — unidades de medida de la distribuidora (UN, CJ, BOL…). */
@Service
@RequiredArgsConstructor
@Transactional
public class UnidadMedidaService {

    private final UnidadMedidaRepository unidadMedidaRepository;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public List<UnidadMedidaDTO> listar() {
        return unidadMedidaRepository.findAllByOrderByCodigoAsc().stream().map(UnidadMedidaDTO::de).toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public UnidadMedidaDTO crear(UnidadMedidaRequest req) {
        String codigo = req.codigo().trim().toUpperCase(Locale.ROOT);
        if (unidadMedidaRepository.existsByCodigoIgnoreCase(codigo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una unidad con ese código");
        }
        UnidadMedida u = new UnidadMedida();
        u.setCodigo(codigo);
        u.setNombre(req.nombre().trim());
        return UnidadMedidaDTO.de(unidadMedidaRepository.save(u));
    }
}
