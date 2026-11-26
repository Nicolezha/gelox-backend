package com.gelox.backend.rf71;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaConsultaService;
import com.gelox.backend.gobierno.AuditoriaConsultaService.Filtros;
import com.gelox.backend.gobierno.AuditoriaRepository;
import com.gelox.backend.gobierno.dto.AuditoriaFilaDTO;
import com.gelox.backend.gobierno.dto.AuditoriaUsuarioDTO;
import com.gelox.backend.gobierno.dto.PaginaDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * RF71 — GET /api/plataforma/auditoria sobre H2: filtros (distribuidora, usuario, desde, hasta,
 * acción), orden por fecha_hora desc, paginación, y nombre de la distribuidora y del usuario
 * en cada fila. También /acciones y /usuarios para llenar los filtros.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(AuditoriaConsultaService.class)
class AuditoriaConsultaTest {

    @Autowired AuditoriaConsultaService consulta;
    @Autowired AuditoriaRepository auditoriaRepository;
    @Autowired DistribuidoraRepository distribuidoraRepository;
    @Autowired UsuarioRepository usuarioRepository;

    private Distribuidora magico;
    private Distribuidora frio;
    private Usuario gerenteMagico;
    private Usuario vendedorFrio;
    private final UUID superadminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        magico = distribuidora("magicosabor", "Mágico Sabor");
        frio = distribuidora("frioexpress", "Frío Express");
        gerenteMagico = usuario("uid-gm", "Ayde Rincón", "ayde@magicosabor.co", RolUsuario.ADMINISTRADOR, magico);
        vendedorFrio = usuario("uid-vf", "Kevin Ortega", "kevin@frioexpress.co", RolUsuario.ENCARGADO_VENTAS, frio);

        fila(LocalDateTime.of(2026, 11, 20, 9, 0), magico.getId(), gerenteMagico, "USUARIO_CREAR", "Usuario", null,
                "{\"nombre\":\"Laura\",\"correo\":\"laura@magicosabor.co\",\"rol\":\"ENCARGADO_VENTAS\"}");
        fila(LocalDateTime.of(2026, 11, 21, 8, 0), frio.getId(), vendedorFrio, "SESION_INICIAR", "Usuario", null,
                "{\"rol\":\"ENCARGADO_VENTAS\"}");
        fila(LocalDateTime.of(2026, 11, 22, 17, 12), null, null, "PLAN_EDITAR", "PlanSuscripcion", UUID.randomUUID(),
                "{\"plan\":\"Estándar\",\"cambios\":[]}");
        // Superadministrador sobre Frío Express: tenant_id null, entidad Distribuidora
        fila(LocalDateTime.of(2026, 11, 23, 10, 30), null, null, "PLAN_ASIGNAR", "Distribuidora", frio.getId(),
                "{\"planAnterior\":\"Básico\",\"planNuevo\":\"Estándar\",\"motivo\":\"Creció\"}");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Distribuidora distribuidora(String subdominio, String nombre) {
        return distribuidoraRepository.saveAndFlush(Distribuidora.builder()
                .razonSocial(nombre).nit("NIT-" + subdominio).nombreComercial(nombre).subdominio(subdominio)
                .correoGerente("g@" + subdominio + ".co").plan("BASICO").planId(UUID.randomUUID())
                .estado(EstadoDistribuidora.ACTIVO).colorPrimario("#9E2016").colorSecundario("#7C0202").build());
    }

    private Usuario usuario(String uid, String nombre, String correo, RolUsuario rol, Distribuidora d) {
        return usuarioRepository.saveAndFlush(Usuario.builder().firebaseUid(uid).nombre(nombre).correo(correo)
                .rol(rol).activo(true).tenantId(d.getId()).build());
    }

    private void fila(LocalDateTime fecha, UUID tenantId, Usuario usuario, String accion, String entidad,
                      UUID entidadId, String detalle) {
        auditoriaRepository.saveAndFlush(Auditoria.builder()
                .fechaHora(fecha).ip("181.50.1.20").tenantId(tenantId)
                .usuarioId(usuario != null ? usuario.getId() : superadminId)
                .rol(usuario != null ? usuario.getRol().name() : "SUPERADMINISTRADOR")
                .accion(accion).entidad(entidad).entidadId(entidadId).detalle(detalle).build());
    }

    private PaginaDTO<AuditoriaFilaDTO> listar(Filtros f) {
        return consulta.listar(f, 0, 10);
    }

    private static Filtros sinFiltros() {
        return new Filtros(null, null, null, null, null);
    }

    @Test
    @DisplayName("Sin filtros: todas las filas por fechaHora desc, con la forma de página de Spring")
    void ordenYPagina() {
        PaginaDTO<AuditoriaFilaDTO> pagina = listar(sinFiltros());

        assertThat(pagina.totalElements()).isEqualTo(4);
        assertThat(pagina.totalPages()).isEqualTo(1);
        assertThat(pagina.number()).isZero();
        assertThat(pagina.size()).isEqualTo(10);
        assertThat(pagina.content()).extracting(AuditoriaFilaDTO::accion)
                .containsExactly("PLAN_ASIGNAR", "PLAN_EDITAR", "SESION_INICIAR", "USUARIO_CREAR");
    }

    @Test
    @DisplayName("Cada fila trae distribuidoraNombre y nombre, correo y rol del usuario; detalle tal como se guardó")
    void filaEnriquecida() {
        AuditoriaFilaDTO fila = listar(new Filtros(null, null, null, null, "USUARIO_CREAR")).content().get(0);

        assertThat(fila.tenantId()).isEqualTo(magico.getId());
        assertThat(fila.distribuidoraNombre()).isEqualTo("Mágico Sabor");
        assertThat(fila.usuarioId()).isEqualTo(gerenteMagico.getId());
        assertThat(fila.usuarioNombre()).isEqualTo("Ayde Rincón");
        assertThat(fila.usuarioCorreo()).isEqualTo("ayde@magicosabor.co");
        assertThat(fila.usuarioRol()).isEqualTo("ADMINISTRADOR");
        assertThat(fila.ip()).isEqualTo("181.50.1.20");
        assertThat(fila.detalle()).isEqualTo(
                "{\"nombre\":\"Laura\",\"correo\":\"laura@magicosabor.co\",\"rol\":\"ENCARGADO_VENTAS\"}");
    }

    @Test
    @DisplayName("Operación de plataforma: distribuidoraNombre null; sobre una distribuidora: su nombre")
    void distribuidoraDeOperacionesDePlataforma() {
        PaginaDTO<AuditoriaFilaDTO> pagina = listar(sinFiltros());

        AuditoriaFilaDTO asignar = pagina.content().get(0);
        assertThat(asignar.tenantId()).isNull();
        assertThat(asignar.distribuidoraNombre()).isEqualTo("Frío Express");
        assertThat(asignar.usuarioRol()).isEqualTo("SUPERADMINISTRADOR");

        AuditoriaFilaDTO editarPlan = pagina.content().get(1);
        assertThat(editarPlan.distribuidoraNombre()).isNull();
    }

    @Test
    @DisplayName("Filtro distribuidoraId: sus filas y las del Superadministrador sobre ella")
    void filtroDistribuidora() {
        assertThat(listar(new Filtros(frio.getId(), null, null, null, null)).content())
                .extracting(AuditoriaFilaDTO::accion).containsExactly("PLAN_ASIGNAR", "SESION_INICIAR");
        assertThat(listar(new Filtros(magico.getId(), null, null, null, null)).content())
                .extracting(AuditoriaFilaDTO::accion).containsExactly("USUARIO_CREAR");
    }

    @Test
    @DisplayName("Filtros usuarioId, acción y rango desde/hasta (inclusivo) combinados")
    void filtrosCombinados() {
        assertThat(listar(new Filtros(null, vendedorFrio.getId(), null, null, null)).totalElements()).isEqualTo(1);
        assertThat(listar(new Filtros(null, null, null, null, "PLAN_EDITAR")).totalElements()).isEqualTo(1);
        assertThat(listar(new Filtros(null, null, LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22), null))
                .content()).extracting(AuditoriaFilaDTO::accion).containsExactly("PLAN_EDITAR", "SESION_INICIAR");
        assertThat(listar(new Filtros(frio.getId(), null, LocalDate.of(2026, 11, 23), null, "PLAN_ASIGNAR"))
                .totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("desde posterior a hasta → 400")
    void rangoInvalido() {
        Throwable t = catchThrowable(() ->
                listar(new Filtros(null, null, LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 1), null)));

        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Paginación: size 3 → 2 páginas; size fuera de rango se ajusta a 1..100")
    void paginacion() {
        PaginaDTO<AuditoriaFilaDTO> segunda = consulta.listar(sinFiltros(), 1, 3);
        assertThat(segunda.totalPages()).isEqualTo(2);
        assertThat(segunda.number()).isEqualTo(1);
        assertThat(segunda.content()).extracting(AuditoriaFilaDTO::accion).containsExactly("USUARIO_CREAR");

        assertThat(consulta.listar(sinFiltros(), -1, 500).size()).isEqualTo(100);
        assertThat(consulta.listar(sinFiltros(), null, null).size()).isEqualTo(10);
    }

    @Test
    @DisplayName("/acciones: los códigos de AccionAuditoria (FP-15 §2.16.3)")
    void acciones() {
        assertThat(consulta.acciones()).contains("PLAN_CREAR", "PLAN_EDITAR", "PLAN_ASIGNAR", "USUARIO_CREAR",
                "DISTRIBUIDORA_CAMBIAR_ESTADO", "ACCESO_BLOQUEADO", "SESION_INICIAR");
    }

    @Test
    @DisplayName("/usuarios: usuarios con filas (de la distribuidora si se indica) con su distribuidoraNombre")
    void usuariosDelFiltro() {
        assertThat(consulta.usuarios(null)).extracting(AuditoriaUsuarioDTO::nombre)
                .containsExactly("Ayde Rincón", "Kevin Ortega");   // el superadmin de prueba no está en usuario

        AuditoriaUsuarioDTO kevin = consulta.usuarios(frio.getId()).get(0);
        assertThat(consulta.usuarios(frio.getId())).hasSize(1);
        assertThat(kevin.correo()).isEqualTo("kevin@frioexpress.co");
        assertThat(kevin.rol()).isEqualTo("ENCARGADO_VENTAS");
        assertThat(kevin.distribuidoraNombre()).isEqualTo("Frío Express");
    }
}
