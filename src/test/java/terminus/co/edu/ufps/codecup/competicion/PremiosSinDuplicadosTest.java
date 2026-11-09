package terminus.co.edu.ufps.codecup.competicion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.PremioCatalogo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.TorneoPremio;
import terminus.co.edu.ufps.codecup.competicion.repository.PremioCatalogoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoPremioRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

/**
 * Bug C5: un torneo no repite premios del catálogo, salvo OTRO, y dos OTRO no comparten título.
 * El rechazo sale como 409 con {@code code = PREMIO_DUPLICADO}, al crear y al editar.
 *
 * <p>El H2 se comparte entre clases: cada test arma su propio torneo. En H2 no corre Flyway, así que
 * el catálogo se siembra aquí y los índices de V4 no existen: esto prueba la validación del servicio.
 * Los índices los prueba {@code FlywayBaselineTest} contra Postgres.
 */
class PremiosSinDuplicadosTest extends AuthTestSupport {

    private static final String PREMIOS = "/api/supercopa/admin/torneos/{torneoId}/premios";

    @Autowired TorneoRepository torneoRepository;
    @Autowired PremioCatalogoRepository premioCatalogoRepository;
    @Autowired TorneoPremioRepository torneoPremioRepository;

    private Usuario admin;

    @BeforeEach
    void catalogoYAdmin() {
        asegurarCatalogo("CAMPEON", "Campeón");
        asegurarCatalogo("OTRO", "Otro");
        admin = cuentaConRoles(Rol.ADMINISTRADOR);
    }

    @Test
    void segundoCampeon_409PremioDuplicado_yQuedaElPrimero() throws Exception {
        var torneo = torneo();
        crear(torneo, premio("CAMPEON", null, 500_000)).andExpect(status().isOk());

        crear(torneo, premio("CAMPEON", null, 300_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREMIO_DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        assertThat(torneoPremioRepository.findByTorneoId(torneo.getId()))
                .extracting(TorneoPremio::getMonto).containsExactly(500_000);
        // La regla es por torneo: otro torneo sí tiene su propio campeón.
        crear(torneo(), premio("CAMPEON", null, 300_000)).andExpect(status().isOk());
    }

    @Test
    void otro_seRepiteConTitulosDistintos_peroNoConElMismoTitulo() throws Exception {
        var torneo = torneo();
        crear(torneo, premio("OTRO", "Juego limpio", 100_000)).andExpect(status().isOk());
        crear(torneo, premio("OTRO", "Mejor barra", 50_000)).andExpect(status().isOk());

        crear(torneo, premio("OTRO", "  JUEGO LIMPIO ", 70_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREMIO_DUPLICADO"));

        assertThat(torneoPremioRepository.findByTorneoId(torneo.getId()))
                .extracting(TorneoPremio::getTitulo)
                .containsExactlyInAnyOrder("Juego limpio", "Mejor barra");
    }

    @Test
    void editarOtro_conElTituloDeOtroOtro_409_yNoLoCambia() throws Exception {
        var torneo = torneo();
        crear(torneo, premio("OTRO", "Juego limpio", 100_000)).andExpect(status().isOk());
        UUID barra = idDe(crear(torneo, premio("OTRO", "Mejor barra", 50_000)));

        editar(torneo, barra, premio(null, "juego limpio ", 70_000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREMIO_DUPLICADO"));

        var guardado = torneoPremioRepository.findById(barra).orElseThrow();
        assertThat(guardado.getTitulo()).isEqualTo("Mejor barra");
        assertThat(guardado.getMonto()).isEqualTo(50_000);
    }

    /** Así edita la web ("Actualizar monto"): PUT al premio que ya existe, con titulo null si no es OTRO. */
    @Test
    void editarComoLaWeb_sigueFuncionando() throws Exception {
        var torneo = torneo();
        UUID campeon = idDe(crear(torneo, premio("CAMPEON", null, 500_000)));
        UUID otro = idDe(crear(torneo, premio("OTRO", "Juego limpio", 100_000)));

        editar(torneo, campeon, premio(null, null, 800_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(800_000));
        editar(torneo, otro, premio(null, "Juego Limpio", 120_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Juego Limpio"))
                .andExpect(jsonPath("$.monto").value(120_000));
    }

    // ── Mundo mínimo ────────────────────────────────────────────────────

    private void asegurarCatalogo(String codigo, String nombre) {
        if (premioCatalogoRepository.findByCodigo(codigo).isEmpty()) {
            premioCatalogoRepository.save(PremioCatalogo.builder().codigo(codigo).nombre(nombre).build());
        }
    }

    /** Spec 0024: el admin lo es de este torneo, no global. */
    private Torneo torneo() {
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.PUBLICADO)
                .build());
        hacerAdmin(admin, torneo.getId());
        return torneo;
    }

    /** El cuerpo que manda la web: descripcion siempre null; codigoCatalogo solo al crear. */
    private static Map<String, Object> premio(String codigo, String titulo, int monto) {
        var cuerpo = new HashMap<String, Object>();
        if (codigo != null) cuerpo.put("codigoCatalogo", codigo);
        cuerpo.put("titulo", titulo);
        cuerpo.put("descripcion", null);
        cuerpo.put("monto", monto);
        return cuerpo;
    }

    private ResultActions crear(Torneo torneo, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(post(PREMIOS, torneo.getId())
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    private ResultActions editar(Torneo torneo, UUID premioId, Map<String, Object> cuerpo) throws Exception {
        return mvc.perform(put(PREMIOS + "/{premioId}", torneo.getId(), premioId)
                .header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(cuerpo)));
    }

    private UUID idDe(ResultActions creado) throws Exception {
        String json = creado.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }
}
