package terminus.co.edu.ufps.codecup.shared.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj con la zona de los torneos (spec 0026). Las fechas de los partidos se guardan sin zona y
 * se entienden en hora de Colombia; en la nube (Render) el servidor corre en UTC, así que "hoy"
 * se calcula con este reloj y no con la zona del sistema.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${app.zona-horaria:America/Bogota}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
