package terminus.co.edu.ufps.codecup.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios 2 y 6 de la spec 0015.
 *
 * <p>MS4 vivía en un binario <b>sin Spring Security</b>: sus endpoints eran públicos.
 * Al fusionarlo dentro de supercopa caen bajo el {@code anyRequest().authenticated()}
 * de {@code SecurityConfig}, así que sin una regla explícita pasarían de 200 a 401 de
 * golpe. Este test falla en rojo si alguien quita ese {@code permitAll}.
 *
 * <p>El último caso es el control: verifica que abrir analytics <b>no</b> abrió el resto
 * de la API por accidente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsEndpointsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TorneoRepository torneoRepository;

    private final UUID torneoId = UUID.randomUUID();

    @Test
    void posicionesEsPublico() throws Exception {
        mockMvc.perform(get("/api/analytics/torneos/{id}/posiciones", torneoId))
                .andExpect(status().isOk());
    }

    @Test
    void goleadoresEsPublico() throws Exception {
        mockMvc.perform(get("/api/analytics/torneos/{id}/goleadores", torneoId))
                .andExpect(status().isOk());
    }

    @Test
    void porterosEsPublico() throws Exception {
        mockMvc.perform(get("/api/analytics/torneos/{id}/porteros", torneoId))
                .andExpect(status().isOk());
    }

    /**
     * Premios se calcula en vivo desde competición, que responde 404 si el torneo
     * no existe. Por eso este caso usa un torneo real: lo que se prueba es que no
     * pida token. Con un UUID al azar ya fallaba en supercopa (404).
     */
    @Test
    void premiosEsPublico() throws Exception {
        var torneo = torneoRepository.save(Torneo.builder().nombre("Copa pública").edicion(2026).build());
        mockMvc.perform(get("/api/analytics/torneos/{id}/premios", torneo.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void elRestoDeLaApiSigueProtegido() throws Exception {
        mockMvc.perform(get("/api/supercopa/torneos"))
                .andExpect(status().isUnauthorized());
    }
}
