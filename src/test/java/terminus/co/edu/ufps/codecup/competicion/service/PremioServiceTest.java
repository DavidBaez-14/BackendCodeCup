package terminus.co.edu.ufps.codecup.competicion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import terminus.co.edu.ufps.codecup.competicion.dto.admin.ActualizarPremioRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.CrearPremioRequest;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.PremioCatalogo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.TorneoPremio;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PremioAsignadoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PremioCatalogoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoPremioRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Bug C5: un torneo no repite premios del catálogo. OTRO es la excepción (la web arma varios,
 * cada uno con su título), pero dos OTRO del mismo torneo no comparten título: se comparan sin
 * mayúsculas ni espacios de los extremos. Editar un premio tampoco puede crear un duplicado.
 */
@ExtendWith(MockitoExtension.class)
class PremioServiceTest {

    @Mock TorneoRepository torneoRepo;
    @Mock PremioCatalogoRepository premioCatalogoRepo;
    @Mock TorneoPremioRepository torneoPremioRepo;
    @Mock PremioAsignadoRepository premioAsignadoRepo;
    @Mock EquipoTorneoRepository equipoTorneoRepo;
    @InjectMocks PremioService service;

    private static final UUID TORNEO = UUID.randomUUID();
    private static final PremioCatalogo CAMPEON = catalogo("CAMPEON", "Campeón");
    private static final PremioCatalogo OTRO = catalogo("OTRO", "Otro");

    /** Hace de tabla torneo_premio: lo que el servicio guarda, y lo que luego consulta. */
    private final List<TorneoPremio> premios = new ArrayList<>();

    @BeforeEach
    void mundo() {
        var torneo = Torneo.builder().id(TORNEO).nombre("Copa").edicion(2026).estado(EstadoTorneo.PUBLICADO).build();
        lenient().when(torneoRepo.findById(TORNEO)).thenReturn(Optional.of(torneo));
        for (var c : List.of(CAMPEON, OTRO)) {
            lenient().when(premioCatalogoRepo.findByCodigo(c.getCodigo())).thenReturn(Optional.of(c));
            lenient().when(premioCatalogoRepo.findById(c.getId())).thenReturn(Optional.of(c));
        }
        lenient().when(torneoPremioRepo.findByTorneoId(TORNEO)).thenAnswer(inv -> List.copyOf(premios));
        lenient().when(torneoPremioRepo.findById(any(UUID.class))).thenAnswer(inv -> premios.stream()
                .filter(tp -> tp.getId().equals(inv.getArgument(0)))
                .findFirst());
        lenient().when(torneoPremioRepo.save(any(TorneoPremio.class))).thenAnswer(inv -> {
            TorneoPremio tp = inv.getArgument(0);
            if (tp.getId() == null) {
                tp.setId(UUID.randomUUID());
                premios.add(tp);
            }
            return tp;
        });
    }

    @Test
    void crearPremio_segundoCampeon_lanza409PremioDuplicado_yNoGuarda() {
        service.crearPremio(TORNEO, crear("CAMPEON", null, 500_000));

        assertThatThrownBy(() -> service.crearPremio(TORNEO, crear("CAMPEON", null, 300_000)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("code", "PREMIO_DUPLICADO");
        assertThat(premios).hasSize(1);
        assertThat(premios.get(0).getMonto()).isEqualTo(500_000);
    }

    @Test
    void crearPremio_dosOtroConTitulosDistintos_guardaAmbos() {
        service.crearPremio(TORNEO, crear("OTRO", "Juego limpio", 100_000));
        service.crearPremio(TORNEO, crear("OTRO", "Mejor barra", 50_000));

        assertThat(premios).extracting(TorneoPremio::getTitulo).containsExactly("Juego limpio", "Mejor barra");
    }

    @Test
    void crearPremio_otroConElMismoTituloEnOtrasMayusculasYEspacios_lanza409_yNoGuarda() {
        service.crearPremio(TORNEO, crear("OTRO", "Juego limpio", 100_000));

        assertThatThrownBy(() -> service.crearPremio(TORNEO, crear("OTRO", "  JUEGO LIMPIO ", 50_000)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("code", "PREMIO_DUPLICADO");
        assertThat(premios).hasSize(1);
    }

    @Test
    void actualizarPremio_otroConElTituloDeOtroOtro_lanza409_yNoLoCambia() {
        service.crearPremio(TORNEO, crear("OTRO", "Juego limpio", 100_000));
        var barra = service.crearPremio(TORNEO, crear("OTRO", "Mejor barra", 50_000));

        assertThatThrownBy(() -> service.actualizarPremio(TORNEO, barra.getId(), editar(" juego LIMPIO", 70_000)))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT)
                .hasFieldOrPropertyWithValue("code", "PREMIO_DUPLICADO");
        var guardado = premios.get(1);
        assertThat(guardado.getTitulo()).isEqualTo("Mejor barra");
        assertThat(guardado.getMonto()).isEqualTo(50_000);
    }

    /** Lo que hace la web en "Actualizar monto": PUT al premio que ya existe. No choca consigo mismo. */
    @Test
    void actualizarPremio_elMismoPremio_noEsDuplicado() {
        var campeon = service.crearPremio(TORNEO, crear("CAMPEON", null, 500_000));
        var otro = service.crearPremio(TORNEO, crear("OTRO", "Juego limpio", 100_000));

        service.actualizarPremio(TORNEO, campeon.getId(), editar(null, 800_000));
        service.actualizarPremio(TORNEO, otro.getId(), editar("Juego Limpio", 120_000));

        assertThat(premios).extracting(TorneoPremio::getMonto).containsExactly(800_000, 120_000);
        assertThat(premios.get(1).getTitulo()).isEqualTo("Juego Limpio");
    }

    // ── Datos ───────────────────────────────────────────────────────────

    private static PremioCatalogo catalogo(String codigo, String nombre) {
        return PremioCatalogo.builder().id(UUID.randomUUID()).codigo(codigo).nombre(nombre).build();
    }

    private static CrearPremioRequest crear(String codigo, String titulo, int monto) {
        var r = new CrearPremioRequest();
        r.setCodigoCatalogo(codigo);
        r.setTitulo(titulo);
        r.setMonto(monto);
        return r;
    }

    private static ActualizarPremioRequest editar(String titulo, int monto) {
        var r = new ActualizarPremioRequest();
        r.setTitulo(titulo);
        r.setMonto(monto);
        return r;
    }
}
