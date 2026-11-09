package terminus.co.edu.ufps.codecup.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PerfilDeportivoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;

/**
 * Spec 0019: el seed reparte los roles (1 admin, 2 árbitros, 4 delegados, 30 jugadores), arma los
 * dos torneos de prueba y es idempotente. Corre sobre H2 con la Admin API de Supabase simulada.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeedServiceTest {

    @Autowired PersonaRepository personas;
    @Autowired CuentaRepository cuentas;
    @Autowired CuentaRolRepository roles;
    @Autowired PerfilDeportivoRepository perfiles;
    @Autowired TorneoRepository torneos;
    @Autowired terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository torneoRoles;
    @Autowired EquipoRepository equipos;
    @Autowired EquipoTorneoRepository inscripciones;
    @Autowired JugadorEquipoRepository planteles;
    @Autowired PartidoRepository partidos;
    @Autowired PlatformTransactionManager transacciones;

    /** Admin API simulada: un id estable por correo y el registro de cuántas veces se pidió cada uno. */
    private final Map<String, Integer> llamadas = new HashMap<>();
    private final CuentasDeSupabase supabaseSimulado = (correo, contrasena, nombre) -> {
        llamadas.merge(correo, 1, Integer::sum);
        return UUID.nameUUIDFromBytes(correo.getBytes(StandardCharsets.UTF_8)).toString();
    };

    private SeedService seed(String contrasena) {
        var repos = new SeedService.Repositorios(personas, cuentas, roles, perfiles, torneos, torneoRoles, equipos,
                inscripciones, planteles, partidos);
        return new SeedService(repos, supabaseSimulado, contrasena, new TransactionTemplate(transacciones));
    }

    @Test
    void sembrar_reparteLosRoles_yArmaLosDosTorneos() {
        seed("clave-de-prueba").sembrar();

        List<Cuenta> deSeed = cuentasDePrueba();
        assertThat(deSeed).hasSize(30);
        assertThat(conRol(deSeed, Rol.ADMINISTRADOR)).isEqualTo(1);
        assertThat(conRol(deSeed, Rol.ARBITRO)).isEqualTo(2);
        assertThat(conRol(deSeed, Rol.DELEGADO)).isEqualTo(4);
        assertThat(conRol(deSeed, Rol.JUGADOR)).isEqualTo(30);

        Torneo copa = torneo(DatosDePrueba.TORNEO_INSCRIPCIONES);
        assertThat(copa.getEstado()).isEqualTo(EstadoTorneo.PUBLICADO);
        assertThat(inscripciones.findByTorneoIdOrderByFechaInscripcionAsc(copa.getId()))
                .extracting(EquipoTorneo::getEstadoInscripcion)
                .containsExactlyInAnyOrder(EstadoInscripcion.APROBADO, EstadoInscripcion.APROBADO,
                        EstadoInscripcion.PENDIENTE_PAGO, EstadoInscripcion.PENDIENTE_PAGO);
        assertThat(partidos.findByTorneoId(copa.getId())).as("la copa queda lista para generar el fixture").isEmpty();

        Torneo liga = torneo(DatosDePrueba.TORNEO_ARBITRO);
        assertThat(liga.getEstado()).isEqualTo(EstadoTorneo.EN_CURSO);

        // Spec 0024: admin01 administra los dos torneos de prueba (sin admins globales) y es SUPERADMIN.
        assertThat(conRol(deSeed, Rol.SUPERADMIN)).isEqualTo(1);
        UUID admin01 = cuentas.findByPersonaId(persona("9000000001")).orElseThrow().getId();
        assertThat(torneoRoles.torneosDe(admin01, terminus.co.edu.ufps.codecup.competicion.model.RolTorneo.ADMIN))
                .containsExactlyInAnyOrder(copa.getId(), liga.getId());
        assertThat(partidos.findByTorneoId(liga.getId()))
                .filteredOn(p -> p.getEstado() == EstadoPartido.PROGRAMADO)
                .hasSize(1);

        // Halcones FC: delegado01 y jugador01 a jugador05.
        var halcones = inscripciones.findByTorneoIdAndDelegadoPersonaId(copa.getId(), persona("9000000201")).orElseThrow();
        assertThat(planteles.findByEquipoTorneoIdOrderByFechaInicioAsc(halcones.getId())).hasSize(6);

        // jugador14 está libre y con perfil (puede pedir unirse); jugador22 no tiene perfil deportivo.
        assertThat(planteles.findByPersonaIdAndTorneoId(persona("9000000314"), copa.getId())).isEmpty();
        assertThat(perfiles.findById(persona("9000000314"))).get()
                .satisfies(j -> assertThat(j.getPosicion()).isNotBlank());
        assertThat(perfiles.findById(persona("9000000322"))).isEmpty();
    }

    @Test
    void sembrarDosVeces_noDuplica_yReutilizaLosUsuarios() {
        seed("clave-de-prueba").sembrar();
        int cuentasAntes = cuentasDePrueba().size();
        long personasAntes = personas.count();
        long torneosAntes = torneos.count();
        long inscripcionesAntes = inscripciones.count();
        long plantelesAntes = planteles.count();
        long partidosAntes = partidos.count();

        seed("clave-de-prueba").sembrar();

        assertThat(cuentasDePrueba()).hasSize(cuentasAntes);
        assertThat(personas.count()).isEqualTo(personasAntes);
        assertThat(torneos.count()).isEqualTo(torneosAntes);
        assertThat(inscripciones.count()).isEqualTo(inscripcionesAntes);
        assertThat(planteles.count()).isEqualTo(plantelesAntes);
        assertThat(partidos.count()).isEqualTo(partidosAntes);
        // La Admin API se consulta en cada corrida; el cliente real reutiliza el usuario si ya existe.
        assertThat(llamadas.get("admin01@prueba.local")).isEqualTo(2);
    }

    @Test
    void siElArbitroCerroElPartido_siembraOtroProgramado() {
        seed("clave-de-prueba").sembrar();
        Torneo liga = torneo(DatosDePrueba.TORNEO_ARBITRO);
        partidos.findByTorneoId(liga.getId()).forEach(p -> {
            p.setEstado(EstadoPartido.FINALIZADO);
            partidos.save(p);
        });

        seed("clave-de-prueba").sembrar();

        var deLaLiga = partidos.findByTorneoId(liga.getId());
        assertThat(deLaLiga).filteredOn(p -> p.getEstado() == EstadoPartido.PROGRAMADO).hasSize(1);
        assertThat(deLaLiga).filteredOn(p -> p.getEstado() == EstadoPartido.PROGRAMADO)
                .singleElement()
                .satisfies(p -> assertThat(p.getJornada()).isGreaterThan(1));
    }

    @Test
    void sinContrasena_fallaConMensajeClaro() {
        assertThatThrownBy(() -> seed(" ").sembrar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SEED_PASSWORD");
        assertThat(llamadas).isEmpty();
    }

    private List<Cuenta> cuentasDePrueba() {
        return cuentas.findAll().stream()
                .filter(c -> c.getCorreo() != null && c.getCorreo().endsWith(DatosDePrueba.DOMINIO))
                .toList();
    }

    private long conRol(List<Cuenta> lista, Rol rol) {
        return lista.stream()
                .filter(c -> roles.findByCuentaIdAndEstado(c.getId(), EstadoRol.APROBADO).stream()
                        .anyMatch(cr -> cr.getRol() == rol))
                .count();
    }

    private Torneo torneo(String nombre) {
        return torneos.findAll().stream()
                .filter(t -> nombre.equals(t.getNombre()))
                .findFirst()
                .orElseThrow();
    }

    /** Persona de un usuario de prueba, por su documento. */
    private UUID persona(String documento) {
        return personas.findFirstByNumeroDocumento(documento).orElseThrow().getId();
    }
}
