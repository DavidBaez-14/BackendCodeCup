package terminus.co.edu.ufps.codecup.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotGoleador;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotGoleadorRepository;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.PremioAsignado;
import terminus.co.edu.ufps.codecup.competicion.model.PremioCatalogo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.TorneoPremio;
import terminus.co.edu.ufps.codecup.competicion.repository.PremioAsignadoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PremioCatalogoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoPremioRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.AuthTestSupport;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;

/**
 * Spec 0022, criterio 15: los endpoints públicos (sin login) de /api/analytics nunca devuelven el
 * documento ni el correo de una persona (hay menores de edad), y muestran su nombre visible.
 */
class PrivacidadPublicaTest extends AuthTestSupport {

    private static final List<String> CAMPOS_PROHIBIDOS = List.of("cedula", "numeroDocumento", "documento", "correo");

    @Autowired TorneoRepository torneoRepository;
    @Autowired PremioCatalogoRepository premioCatalogoRepository;
    @Autowired TorneoPremioRepository torneoPremioRepository;
    @Autowired PremioAsignadoRepository premioAsignadoRepository;
    @Autowired SnapshotGoleadorRepository snapshotGoleadorRepository;

    @Test
    void premiosYGoleadores_sinDocumentoNiCorreo_yMenorConNombreVisible() throws Exception {
        var menor = personaRepository.save(Persona.builder()
                .nombres("Tomás Andrés").apellidos("Niño Rojas")
                .tipoDocumento(TipoDocumento.TI).numeroDocumento(documentoAlAzar())
                .build());
        var torneo = torneoRepository.save(Torneo.builder()
                .nombre("Copa pública " + UUID.randomUUID().toString().substring(0, 8))
                .edicion(2026)
                .estado(EstadoTorneo.EN_CURSO)
                .build());
        var goleador = premioCatalogoRepository.findByCodigo("GOLEADOR").orElseGet(() ->
                premioCatalogoRepository.save(PremioCatalogo.builder().codigo("GOLEADOR").nombre("Goleador").build()));
        var premio = torneoPremioRepository.save(TorneoPremio.builder()
                .torneoId(torneo.getId()).premioId(goleador.getId()).monto(100_000)
                .build());
        premioAsignadoRepository.save(PremioAsignado.builder()
                .torneoId(torneo.getId()).premioId(premio.getId()).personaId(menor.getId())
                .build());
        snapshotGoleadorRepository.save(SnapshotGoleador.builder()
                .torneoId(torneo.getId()).personaId(menor.getId())
                .jugadorNombre("Tomás Andrés Niño Rojas").equipoNombre("Cóndores FC")
                .goles(3).posicion(1)
                .build());

        String premios = mvc.perform(get("/api/analytics/torneos/{id}/premios", torneo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ganador.personaId").value(menor.getId().toString()))
                .andExpect(jsonPath("$[0].ganador.nombre").value("Tomás Andrés N."))
                .andReturn().getResponse().getContentAsString();
        String goleadores = mvc.perform(get("/api/analytics/torneos/{id}/goleadores", torneo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].personaId").value(menor.getId().toString()))
                .andExpect(jsonPath("$[0].jugadorNombre").value("Tomás Andrés N."))
                .andReturn().getResponse().getContentAsString();

        for (String campo : CAMPOS_PROHIBIDOS) {
            assertThat(premios).doesNotContain("\"" + campo + "\"");
            assertThat(goleadores).doesNotContain("\"" + campo + "\"");
        }
        assertThat(premios + goleadores).doesNotContain(menor.getNumeroDocumento());
    }
}
