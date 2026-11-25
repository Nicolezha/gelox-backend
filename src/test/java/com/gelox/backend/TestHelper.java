package com.gelox.backend;

import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.gelox.backend.exportacion.ExportadorPedido;
import com.gelox.backend.plataforma.IdentidadDistribuidoraService;
import com.gelox.backend.repositories.CodigoHomologacionRepository;
import com.gelox.backend.repositories.PlantillaExportacionRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

public final class TestHelper {

    private TestHelper() {}

    /**
     * RF67 — ExportadorPedido con repositorios simulados y el catálogo real de Nutresa
     * (templates/catalogo.xlsx), para los tests que exportan pedidos sin levantar Spring.
     */
    public static ExportadorPedido exportador(IdentidadDistribuidoraService identidad) {
        ExportadorPedido exportador = new ExportadorPedido(
                org.mockito.Mockito.mock(PlantillaExportacionRepository.class),
                org.mockito.Mockito.mock(CodigoHomologacionRepository.class),
                identidad);
        ReflectionTestUtils.setField(exportador, "plantillaExcel", new ClassPathResource("templates/catalogo.xlsx"));
        return exportador;
    }

    public static Usuario buildUsuario(String uid, String correo, RolUsuario rol, boolean activo) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .firebaseUid(uid)
                .nombre("Usuario Test")
                .correo(correo)
                .rol(rol)
                .activo(activo)
                .build();
    }

    public static Authentication authParaUsuario(Usuario usuario) {
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())
        );
        return new UsernamePasswordAuthenticationToken(usuario, null, authorities);
    }
}
