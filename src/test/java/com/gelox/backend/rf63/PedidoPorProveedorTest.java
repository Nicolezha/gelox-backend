package com.gelox.backend.rf63;

import com.gelox.backend.TestHelper;
import com.gelox.backend.controllers.InventarioController;
import com.gelox.backend.dto.AccionPedido;
import com.gelox.backend.dto.CrearPedidoRequest;
import com.gelox.backend.dto.ItemPedidoRequest;
import com.gelox.backend.dto.ModificarPedidoRequest;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.plataforma.IdentidadDistribuidoraService;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.dto.MetricasRecomendacionDTO;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
import com.gelox.backend.repositories.ItemPedidoProveedorRepository;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.services.PedidoProveedorService.PedidoCreado;
import com.gelox.backend.services.PedidoProveedorService.ProveedorPedido;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.GenerarPedidoHandler;
import com.gelox.backend.voz.handlers.ModificarPedidoHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.gelox.backend.recomendacion.RecomendacionTestData.TENANT;
import static com.gelox.backend.recomendacion.RecomendacionTestData.item;
import static com.gelox.backend.recomendacion.RecomendacionTestData.recomendacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF63 (T194) — un pedido por proveedor: PedidoProveedorService.crearPedido, monto mínimo,
 * DecisionRecomendacionService.generarPedido y la voz (GenerarPedidoHandler, ModificarPedidoHandler).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoPorProveedorTest {

    Proveedor nutresa;
    Proveedor qaUno;
    Producto festival;     // Nutresa
    Producto soloLack;     // Nutresa
    Producto qaPaleta;     // Proveedor QA Uno
    Usuario usuario;

    @BeforeEach
    void datos() {
        TenantContext.set(TENANT);
        nutresa = proveedor("Nutresa", "0");
        qaUno = proveedor("Proveedor QA Uno", "300000");
        festival = producto("Festival", nutresa, "24000");
        soloLack = producto("Solo Lack", nutresa, "36000");
        qaPaleta = producto("Paleta QA", qaUno, "100000");
        usuario = TestHelper.buildUsuario("uid-1", "admin@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    static Proveedor proveedor(String nombre, String montoMinimo) {
        Proveedor p = new Proveedor();
        p.setId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setNombre(nombre);
        p.setMontoMinimoPedido(new BigDecimal(montoMinimo));
        return p;
    }

    static Producto producto(String nombre, Proveedor proveedor, String precioCaja) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setNombre(nombre);
        p.setCodigoTecnico(nombre.toUpperCase().replace(' ', '-'));
        p.setUnidadesPorCaja(12);
        p.setPrecioCosto(new BigDecimal(precioCaja));
        p.setProveedor(proveedor);
        return p;
    }

    static PedidoCreado creado(Proveedor proveedor, String total, String advertencia) {
        return new PedidoCreado(UUID.randomUUID(), proveedor.getId(), proveedor.getNombre(), new BigDecimal(total),
                "Pedido_" + proveedor.getNombre() + ".xlsx", new byte[]{1}, List.of(), List.of(), advertencia);
    }

    // ══════════════════════════════════════════════════════════════════════
    // PedidoProveedorService.crearPedido
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class CrearPedido {

        @Mock PedidoProveedorRepository pedidoRepo;
        @Mock ProductoRepository productoRepo;
        @Mock ProveedorRepository proveedorRepo;
        @Mock EventoSistemaService eventoService;

        PedidoProveedorService service;

        @BeforeEach
        void armar() {
            service = new PedidoProveedorService(pedidoRepo, null, productoRepo, null, null, eventoService,
                    TestHelper.exportador(mock(IdentidadDistribuidoraService.class)), null, proveedorRepo);
            when(pedidoRepo.save(any())).thenAnswer(i -> {
                PedidoProveedor p = i.getArgument(0);
                if (p.getId() == null) p.setId(UUID.randomUUID());
                return p;
            });
            for (Producto p : List.of(festival, soloLack, qaPaleta)) {
                when(productoRepo.findById(p.getId())).thenReturn(Optional.of(p));
            }
        }

        private CrearPedidoRequest pedir(ItemPedidoRequest... items) {
            return new CrearPedidoRequest(List.of(items), "Prueba T194");
        }

        @Test
        @DisplayName("CP-63-07: productos de dos proveedores → dos pedidos, en el orden en que aparecen")
        void unPedidoPorProveedor() {
            List<PedidoCreado> creados = service.crearPedido(pedir(
                    new ItemPedidoRequest(festival.getId(), 2, 0),
                    new ItemPedidoRequest(qaPaleta.getId(), 3, 0),
                    new ItemPedidoRequest(soloLack.getId(), 1, 0)), usuario);

            assertThat(creados).extracting(PedidoCreado::proveedorNombre).containsExactly("Nutresa", "Proveedor QA Uno");
            assertThat(creados).extracting(PedidoCreado::proveedorId).containsExactly(nutresa.getId(), qaUno.getId());

            ArgumentCaptor<PedidoProveedor> guardados = ArgumentCaptor.forClass(PedidoProveedor.class);
            verify(pedidoRepo, times(2)).save(guardados.capture());
            PedidoProveedor aNutresa = guardados.getAllValues().get(0);
            PedidoProveedor aQaUno = guardados.getAllValues().get(1);
            assertThat(aNutresa.getProveedor()).isSameAs(nutresa);
            assertThat(aNutresa.getItems()).extracting(i -> i.getProducto().getNombre())
                    .containsExactly("Festival", "Solo Lack");
            assertThat(aNutresa.getItems()).allMatch(i -> i.getPedido() == aNutresa);
            assertThat(aNutresa.getNotas()).isEqualTo("Prueba T194");
            assertThat(aQaUno.getProveedor()).isSameAs(qaUno);
            assertThat(aQaUno.getItems()).extracting(i -> i.getProducto().getNombre()).containsExactly("Paleta QA");
            assertThat(aQaUno.getEstado()).isEqualTo(EstadoPedido.PENDIENTE);

            assertThat(creados.get(0).pedidoId()).isEqualTo(aNutresa.getId());
            assertThat(creados.get(0).exportUrl()).isEqualTo("/api/inventario/pedidos/" + aNutresa.getId() + "/exportar");
            assertThat(creados.get(0).nombreArchivo()).startsWith("Pedido_Nutresa_");
            assertThat(creados.get(1).nombreArchivo()).startsWith("Pedido_Proveedor-QA-Uno_");
            assertThat(creados).allMatch(c -> c.excel().length > 0);
            verify(eventoService, times(2)).registrarEvento(any(), any(), any());
        }

        @Test
        @DisplayName("total = cajas × precio + unidades × precio ÷ unidades_por_caja")
        void totalEstimado() {
            List<PedidoCreado> creados = service.crearPedido(pedir(
                    new ItemPedidoRequest(festival.getId(), 2, 6),        // 48.000 + 12.000
                    new ItemPedidoRequest(soloLack.getId(), 1, 0)), usuario);   // 36.000

            assertThat(creados).hasSize(1);
            assertThat(creados.get(0).total()).isEqualByComparingTo("96000");
            assertThat(creados.get(0).advertenciaMontoMinimo()).isNull();      // Nutresa sin mínimo
        }

        @Test
        @DisplayName("bajo el monto mínimo: el pedido se crea igual y trae la advertencia")
        void bajoElMontoMinimo() {
            List<PedidoCreado> creados = service.crearPedido(pedir(
                    new ItemPedidoRequest(qaPaleta.getId(), 2, 6)), usuario);   // 200.000 + 50.000

            assertThat(creados).hasSize(1);
            assertThat(creados.get(0).total()).isEqualByComparingTo("250000");
            assertThat(creados.get(0).advertenciaMontoMinimo())
                    .isEqualTo("El pedido a Proveedor QA Uno suma $ 250.000 y su monto mínimo es $ 300.000.");
            verify(pedidoRepo).save(any());
        }

        @Test
        @DisplayName("justo en el monto mínimo no hay advertencia")
        void enElMontoMinimo() {
            List<PedidoCreado> creados = service.crearPedido(pedir(
                    new ItemPedidoRequest(qaPaleta.getId(), 3, 0)), usuario);

            assertThat(creados.get(0).advertenciaMontoMinimo()).isNull();
        }

        @Test
        @DisplayName("producto sin proveedor → va al pedido de Nutresa de la distribuidora")
        void sinProveedorVaANutresa() {
            Producto huerfano = producto("Sin proveedor", null, "10000");
            when(productoRepo.findById(huerfano.getId())).thenReturn(Optional.of(huerfano));
            when(proveedorRepo.findFirstByNombreIgnoreCaseAndActivoTrue("Nutresa")).thenReturn(Optional.of(nutresa));

            List<PedidoCreado> creados = service.crearPedido(pedir(
                    new ItemPedidoRequest(huerfano.getId(), 1, 0),
                    new ItemPedidoRequest(festival.getId(), 1, 0)), usuario);

            assertThat(creados).hasSize(1);
            assertThat(creados.get(0).proveedorId()).isEqualTo(nutresa.getId());
            assertThat(service.proveedoresDe(List.of(huerfano.getId(), qaPaleta.getId())))
                    .containsEntry(huerfano.getId(), new ProveedorPedido(nutresa.getId(), "Nutresa"))
                    .containsEntry(qaPaleta.getId(), new ProveedorPedido(qaUno.getId(), "Proveedor QA Uno"));
        }

        @Test
        @DisplayName("producto sin proveedor y sin Nutresa → 400 y no se guarda nada")
        void sinProveedorNiNutresa() {
            Producto huerfano = producto("Sin proveedor", null, "10000");
            when(productoRepo.findById(huerfano.getId())).thenReturn(Optional.of(huerfano));
            when(proveedorRepo.findFirstByNombreIgnoreCaseAndActivoTrue("Nutresa")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.crearPedido(pedir(
                    new ItemPedidoRequest(festival.getId(), 1, 0),
                    new ItemPedidoRequest(huerfano.getId(), 1, 0)), usuario))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getReason()).isEqualTo("El producto Sin proveedor no tiene proveedor asignado");
                    });
            verify(pedidoRepo, never()).save(any());
        }

        @Test
        @DisplayName("modificar: agregar un producto de otro proveedor al pedido → 409")
        void agregarDeOtroProveedor() {
            PedidoProveedor pedido = PedidoProveedor.builder().id(UUID.randomUUID()).tenantId(TENANT)
                    .proveedor(nutresa).fecha(LocalDate.now()).estado(EstadoPedido.PENDIENTE).build();
            pedido.setItems(new ArrayList<>(List.of(ItemPedidoProveedor.builder()
                    .pedido(pedido).producto(festival).cantidadCajas(1).cantidadUnidades(0).build())));
            when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));

            assertThatThrownBy(() -> service.modificarPedidoPendiente(pedido.getId(),
                    new ModificarPedidoRequest(AccionPedido.AGREGAR, qaPaleta.getId(), 1, 0), usuario))
                    .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getReason()).isEqualTo("Paleta QA es de Proveedor QA Uno y el pedido es de Nutresa");
                    });
            assertThat(pedido.getItems()).hasSize(1);
        }

        @Test
        @DisplayName("POST /api/inventario/pedidos con varios proveedores → un .zip con un archivo por pedido")
        void zipConUnArchivoPorPedido() throws Exception {
            PedidoCreado a = new PedidoCreado(UUID.randomUUID(), nutresa.getId(), "Nutresa", BigDecimal.ONE,
                    "Pedido_Nutresa_2026-11-24.xlsx", new byte[]{1, 2}, List.of(), List.of(), null);
            PedidoCreado b = new PedidoCreado(UUID.randomUUID(), qaUno.getId(), "QA", BigDecimal.ONE,
                    "Pedido_QA_2026-11-24.xlsx", new byte[]{3}, List.of(), List.of(), null);
            PedidoCreado c = new PedidoCreado(UUID.randomUUID(), qaUno.getId(), "QA", BigDecimal.ONE,
                    "Pedido_QA_2026-11-24.xlsx", new byte[]{4}, List.of(), List.of(), null);

            List<String> nombres = new ArrayList<>();
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(InventarioController.comprimir(List.of(a, b, c))))) {
                for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) nombres.add(e.getName());
            }
            assertThat(nombres).containsExactly("Pedido_Nutresa_2026-11-24.xlsx", "Pedido_QA_2026-11-24.xlsx",
                    "Pedido_QA_2026-11-24_2.xlsx");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DecisionRecomendacionService — RF56 con varios proveedores
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class DesdeRecomendacion {

        @Mock RecomendacionCompraRepository recomendacionRepo;
        @Mock PedidoProveedorRepository pedidoRepo;
        @Mock ItemPedidoProveedorRepository itemPedidoRepo;
        @Mock PedidoProveedorService pedidoService;
        @Mock EventoSistemaService eventoService;

        DecisionRecomendacionService service;
        RecomendacionCompra recomendacion;
        ItemRecomendacion deNutresa;
        ItemRecomendacion deQaUno;

        @BeforeEach
        void armar() {
            service = new DecisionRecomendacionService(recomendacionRepo, pedidoRepo, itemPedidoRepo,
                    pedidoService, eventoService);
            deNutresa = item(festival, 1, 10, "240000.00");
            deQaUno = item(qaPaleta, 2, 10, "1000000.00");
            recomendacion = recomendacion(TENANT, EstadoRecomendacion.EN_REVISION, deNutresa, deQaUno);
            when(recomendacionRepo.findById(recomendacion.getId())).thenReturn(Optional.of(recomendacion));
            when(recomendacionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        }

        private PedidoProveedor pedido(UUID id, Proveedor proveedor, EstadoPedido estado) {
            return PedidoProveedor.builder().id(id).tenantId(TENANT).proveedor(proveedor).estado(estado).build();
        }

        @Test
        @DisplayName("CP-63-07: devuelve pedidoId y exportUrl del primero y la lista pedidos con total y advertencia")
        void dosPedidos() {
            deNutresa.aprobar();
            deQaUno.modificar(2);
            PedidoCreado aNutresa = creado(nutresa, "240000.00", null);
            PedidoCreado aQaUno = creado(qaUno, "200000.00",
                    "El pedido a Proveedor QA Uno suma $ 200.000 y su monto mínimo es $ 300.000.");
            when(pedidoService.crearPedido(any(), any())).thenReturn(List.of(aNutresa, aQaUno));
            PedidoProveedor primero = pedido(aNutresa.pedidoId(), nutresa, EstadoPedido.PENDIENTE);
            when(pedidoRepo.findById(aNutresa.pedidoId())).thenReturn(Optional.of(primero));

            PedidoGeneradoDTO dto = service.generarPedido(recomendacion.getId(), usuario);

            assertThat(dto.pedidoId()).isEqualTo(aNutresa.pedidoId());
            assertThat(dto.exportUrl()).isEqualTo("/api/inventario/pedidos/" + aNutresa.pedidoId() + "/exportar");
            assertThat(dto.pedidos()).hasSize(2);
            PedidoGeneradoDTO.PedidoDeProveedorDTO segundo = dto.pedidos().get(1);
            assertThat(segundo.pedidoId()).isEqualTo(aQaUno.pedidoId());
            assertThat(segundo.proveedorId()).isEqualTo(qaUno.getId());
            assertThat(segundo.proveedorNombre()).isEqualTo("Proveedor QA Uno");
            assertThat(segundo.total()).isEqualByComparingTo("200000");
            assertThat(segundo.exportUrl()).isEqualTo("/api/inventario/pedidos/" + aQaUno.pedidoId() + "/exportar");
            assertThat(segundo.advertenciaMontoMinimo()).contains("monto mínimo");
            assertThat(dto.pedidos().get(0).advertenciaMontoMinimo()).isNull();

            // Un solo crearPedido con todos los ítems: el servicio de pedidos es quien parte por proveedor
            ArgumentCaptor<CrearPedidoRequest> req = ArgumentCaptor.forClass(CrearPedidoRequest.class);
            verify(pedidoService).crearPedido(req.capture(), any());
            assertThat(req.getValue().items()).containsExactly(
                    new ItemPedidoRequest(festival.getId(), 10, 0), new ItemPedidoRequest(qaPaleta.getId(), 2, 0));
            assertThat(req.getValue().notas())
                    .isEqualTo("Generado desde recomendación #" + recomendacion.getId().toString().substring(0, 8));

            assertThat(recomendacion.getPedidoProveedor()).isSameAs(primero);
            assertThat(recomendacion.getEstado()).isEqualTo(EstadoRecomendacion.PEDIDO_GENERADO);
            ArgumentCaptor<String> evento = ArgumentCaptor.forClass(String.class);
            verify(eventoService).registrarEvento(any(), evento.capture(), any());
            assertThat(evento.getValue()).startsWith("2 pedidos #").contains(" a Nutresa, #").contains(" a Proveedor QA Uno generados");
        }

        @Test
        @DisplayName("RF57: la precisión junta lo recibido en los pedidos de todos los proveedores")
        void metricasConVariosPedidos() {
            deNutresa.aprobar();
            deQaUno.aprobar();
            recomendacion.setEstado(EstadoRecomendacion.PEDIDO_GENERADO);
            PedidoProveedor p1 = pedido(UUID.randomUUID(), nutresa, EstadoPedido.RECIBIDO);
            PedidoProveedor p2 = pedido(UUID.randomUUID(), qaUno, EstadoPedido.RECIBIDO);
            recomendacion.setPedidoProveedor(p1);
            when(recomendacionRepo.buscarConItems(eq(TENANT), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(recomendacion));
            when(pedidoRepo.findByNotas("Generado desde recomendación #" + recomendacion.getId().toString().substring(0, 8)))
                    .thenReturn(List.of(p1, p2));
            when(itemPedidoRepo.findByPedidoIdWithProducto(p1.getId())).thenReturn(List.of(
                    ItemPedidoProveedor.builder().producto(festival).cantidadRecibida(120).build()));   // 10 cajas
            when(itemPedidoRepo.findByPedidoIdWithProducto(p2.getId())).thenReturn(List.of(
                    ItemPedidoProveedor.builder().producto(qaPaleta).cantidadRecibida(60).build()));    // 5 cajas

            MetricasRecomendacionDTO m = service.metricas(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

            // Festival: pedidas 1,0 y recibidas 1,0 · Paleta QA: pedidas 1,0 y recibidas 1 − 5/10 = 0,5
            assertThat(m.precisionCantidad()).isEqualTo(0.875);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Voz — GenerarPedidoHandler y ModificarPedidoHandler
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    class Voz {

        @Mock ResolvedorProducto resolvedor;
        @Mock PedidoProveedorService pedidoService;
        @Mock PedidoProveedorRepository pedidoRepo;

        GenerarPedidoHandler generar;
        ModificarPedidoHandler modificar;

        @BeforeEach
        void armar() {
            generar = new GenerarPedidoHandler(resolvedor, pedidoService);
            modificar = new ModificarPedidoHandler(pedidoRepo, resolvedor, pedidoService);
            for (Producto p : List.of(festival, soloLack, qaPaleta)) {
                when(resolvedor.resolver(p.getNombre().toLowerCase())).thenReturn(List.of(
                        new ResolvedorProducto.ProductoCandidato(p.getId(), p.getNombre(), 1.0, 12)));
            }
            when(pedidoService.proveedoresDe(anyCollection())).thenAnswer(i -> {
                Map<UUID, ProveedorPedido> m = new java.util.LinkedHashMap<>();
                for (Object id : (java.util.Collection<?>) i.getArgument(0)) {
                    Producto p = List.of(festival, soloLack, qaPaleta).stream()
                            .filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
                    m.put(p.getId(), new ProveedorPedido(p.getProveedor().getId(), p.getProveedor().getNombre()));
                }
                return m;
            });
        }

        private VozContexto ctx(String texto) {
            return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.of(2026, 11, 24));
        }

        private PedidoProveedor pendiente(Proveedor proveedor, Producto producto) {
            PedidoProveedor p = PedidoProveedor.builder().id(UUID.randomUUID()).tenantId(TENANT).proveedor(proveedor)
                    .fecha(LocalDate.of(2026, 11, 20)).createdAt(LocalDateTime.of(2026, 11, 20, 8, 0))
                    .estado(EstadoPedido.PENDIENTE).build();
            p.setItems(new ArrayList<>(List.of(ItemPedidoProveedor.builder().pedido(p).producto(producto)
                    .cantidadCajas(5).cantidadUnidades(0).build())));
            when(pedidoRepo.findByIdWithItems(p.getId())).thenReturn(Optional.of(p));
            return p;
        }

        @Test
        @DisplayName("CP-67-05: productos de dos proveedores → «Se generarán 2 pedidos: …»")
        void resumenConDosProveedores() {
            VozResultado r = generar.interpretar(ctx("genera un pedido con 20 cajas de Solo Lack y 15 de Paleta QA"));

            assertThat(r.ok()).isTrue();
            assertThat(r.textoRespuesta()).isEqualTo("Se generarán 2 pedidos: a Nutresa, 20 cajas de Solo Lack; "
                    + "a Proveedor QA Uno, 15 cajas de Paleta QA. ¿Confirmas?");
        }

        @Test
        @DisplayName("un solo proveedor → «Pedido a Nutresa: …»")
        void resumenConUnProveedor() {
            VozResultado r = generar.interpretar(ctx("genera un pedido con 20 cajas de Solo Lack y 15 de Festival"));

            assertThat(r.textoRespuesta())
                    .isEqualTo("Pedido a Nutresa: 20 cajas de Solo Lack y 15 cajas de Festival. ¿Confirmas?");
        }

        @Test
        @DisplayName("producto sin proveedor ni Nutresa → no pide confirmación y dice qué falta")
        void sinProveedorAsignado() {
            when(pedidoService.proveedoresDe(anyCollection())).thenThrow(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El producto Festival no tiene proveedor asignado"));

            VozResultado r = generar.interpretar(ctx("genera un pedido con 2 cajas de Festival"));

            assertThat(r.ok()).isFalse();
            assertThat(r.textoRespuesta()).isEqualTo(
                    "El producto Festival no tiene proveedor asignado. Asígnale uno en el catálogo.");
        }

        @Test
        @DisplayName("confirmar → datos con pedidoId, exportUrl y pedidos; el texto avisa el monto mínimo")
        void ejecutarConDosPedidos() {
            CrearPedidoRequest req = new CrearPedidoRequest(List.of(), "Generado por voz");
            PedidoCreado aNutresa = creado(nutresa, "720000.00", null);
            PedidoCreado aQaUno = creado(qaUno, "100000.00",
                    "El pedido a Proveedor QA Uno suma $ 100.000 y su monto mínimo es $ 300.000.");
            when(pedidoService.crearPedido(req, usuario)).thenReturn(List.of(aNutresa, aQaUno));

            VozResultado r = generar.ejecutar(new VozPendiente(UUID.randomUUID(), usuario.getId(),
                    TipoIntencionVoz.GENERAR_PEDIDO, req, Instant.now()), usuario);

            assertThat(r.ok()).isTrue();
            assertThat(r.textoRespuesta()).isEqualTo("Se registraron 2 pedidos, uno por proveedor. Ya puedes descargar "
                    + "los archivos. Ojo: El pedido a Proveedor QA Uno suma $ 100.000 y su monto mínimo es $ 300.000.");
            assertThat(r.datos()).containsEntry("pedidoId", aNutresa.pedidoId())
                    .containsEntry("exportUrl", aNutresa.exportUrl());
            @SuppressWarnings("unchecked")
            List<PedidoGeneradoDTO.PedidoDeProveedorDTO> pedidos =
                    (List<PedidoGeneradoDTO.PedidoDeProveedorDTO>) r.datos().get("pedidos");
            assertThat(pedidos).extracting(PedidoGeneradoDTO.PedidoDeProveedorDTO::proveedorNombre)
                    .containsExactly("Nutresa", "Proveedor QA Uno");
        }

        @Test
        @DisplayName("modificar con dos pendientes sin decir cuál → elige el del proveedor del producto")
        void modificarInfiereElPedido() {
            PedidoProveedor deNutresa = pendiente(nutresa, soloLack);
            PedidoProveedor deQaUno = pendiente(qaUno, qaPaleta);
            when(pedidoRepo.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(deNutresa, deQaUno));

            VozResultado r = modificar.interpretar(ctx("agregale 10 cajas de festival"));

            assertThat(r.ok()).isTrue();
            assertThat(r.datos()).containsEntry("pedidoId", deNutresa.getId()).containsEntry("cantidadNueva", 10);
            assertThat(r.textoRespuesta()).startsWith("Al pedido #" + deNutresa.getId().toString().substring(0, 8).toUpperCase());
        }

        @Test
        @DisplayName("modificar con dos pendientes del mismo proveedor del producto → pregunta solo entre esos")
        void modificarPreguntaEntreLosDelProveedor() {
            PedidoProveedor n1 = pendiente(nutresa, soloLack);
            PedidoProveedor n2 = pendiente(nutresa, soloLack);
            PedidoProveedor q = pendiente(qaUno, qaPaleta);
            when(pedidoRepo.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(n1, n2, q));

            VozResultado r = modificar.interpretar(ctx("agregale 10 cajas de festival"));

            assertThat(r.ok()).isFalse();
            assertThat(r.textoRespuesta()).startsWith("Hay 2 pedidos pendientes: 1) del 20 de noviembre a Nutresa con Solo Lack");
        }

        @Test
        @DisplayName("modificar: agregar al único pendiente un producto de otro proveedor → no se hace")
        void modificarRechazaOtroProveedor() {
            PedidoProveedor deNutresa = pendiente(nutresa, soloLack);
            when(pedidoRepo.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(deNutresa));

            VozResultado r = modificar.interpretar(ctx("agregale 10 cajas de paleta qa"));

            assertThat(r.ok()).isFalse();
            assertThat(r.textoRespuesta()).isEqualTo("Paleta QA es de Proveedor QA Uno y el pedido #"
                    + deNutresa.getId().toString().substring(0, 8).toUpperCase()
                    + " es de Nutresa. Genera un pedido nuevo para Proveedor QA Uno.");
        }
    }
}
