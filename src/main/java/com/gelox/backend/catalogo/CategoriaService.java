package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.CategoriaDTO;
import com.gelox.backend.catalogo.dto.CategoriaRequest;
import com.gelox.backend.entities.Categoria;
import com.gelox.backend.repositories.CategoriaRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** RF64 — categorías del catálogo de la distribuidora. */
@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public List<CategoriaDTO> listar() {
        return categoriaRepository.findAllByOrderByOrdenAscNombreAsc().stream().map(CategoriaDTO::de).toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public CategoriaDTO crear(CategoriaRequest req) {
        String nombre = req.nombre().trim();
        if (categoriaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre");
        }
        Categoria c = new Categoria();
        c.setNombre(nombre);
        if (req.orden() != null) c.setOrden(req.orden());
        return CategoriaDTO.de(categoriaRepository.save(c));
    }
}
