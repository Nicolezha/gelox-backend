package com.gelox.backend.ventas.rural;

import com.gelox.backend.entities.ClienteRural;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.ventas.rural.dto.ClienteRuralDTO;
import com.gelox.backend.ventas.rural.dto.CrearClienteRuralRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * RF33 — Lógica de negocio para clientes rurales.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteRuralService {

    private final ClienteRuralRepository clienteRuralRepository;

    // ─────────────────────────── RF33 — GET ──────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public List<ClienteRuralDTO> listarClientes(String q) {
        List<ClienteRural> clientes;

        if (q != null && !q.isBlank()) {
            clientes = clienteRuralRepository.buscarPorNombreOTelefono(q.trim());
        } else {
            clientes = clienteRuralRepository.findAllByOrderByNombreAsc();
        }

        return clientes.stream().map(this::toDTO).toList();
    }

    // ─────────────────────────── RF33 — POST ─────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public ClienteRuralDTO crearCliente(CrearClienteRuralRequest req) {
        // 1. Dedup por teléfono si viene presente
        if (req.telefono() != null && !req.telefono().isBlank()) {
            clienteRuralRepository.findByTelefono(req.telefono().trim())
                    .ifPresent(existente -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Este cliente ya está registrado");
                    });
        }

        // 2. Insertar con recurrente = true (se registra para uso recurrente)
        ClienteRural cliente = ClienteRural.builder()
                .nombre(req.nombre().trim())
                .telefono(req.telefono() != null ? req.telefono().trim() : null)
                .direccion(req.direccion())
                .corregimiento(req.corregimiento())
                .recurrente(true)
                .build();

        ClienteRural guardado = clienteRuralRepository.save(cliente);
        return toDTO(guardado);
    }

    // ─────────────────────────── Helper ──────────────────────────────

    private ClienteRuralDTO toDTO(ClienteRural c) {
        return new ClienteRuralDTO(
                c.getId(),
                c.getNombre(),
                c.getTelefono(),
                c.getDireccion(),
                c.getCorregimiento(),
                c.getRecurrente()
        );
    }
}
