package com.gelox.backend;

import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

public final class TestHelper {

    private TestHelper() {}

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
