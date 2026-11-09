package terminus.co.edu.ufps.codecup.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PerfilDeportivoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;

/**
 * Corre el seed al arrancar con el perfil {@code seed} y cierra la app (levanta en un puerto
 * aleatorio: ver application-seed.properties). Uso: {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=seed}.
 */
@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    private final PersonaRepository personas;
    private final CuentaRepository cuentas;
    private final CuentaRolRepository roles;
    private final PerfilDeportivoRepository perfiles;
    private final TorneoRepository torneos;
    private final terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository torneoRoles;
    private final EquipoRepository equipos;
    private final EquipoTorneoRepository inscripciones;
    private final JugadorEquipoRepository planteles;
    private final PartidoRepository partidos;
    private final terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository partidoArbitros;
    private final CuentasDeSupabase supabase;
    private final PlatformTransactionManager transacciones;
    private final ConfigurableApplicationContext contexto;

    /** Una sola contraseña para todos los usuarios de prueba. Llega por .env; nunca va en el código. */
    @Value("${SEED_PASSWORD:${env.SEED_PASSWORD:}}")
    private String contrasena;

    @Override
    public void run(ApplicationArguments args) {
        var repos = new SeedService.Repositorios(personas, cuentas, roles, perfiles, torneos, torneoRoles, equipos,
                inscripciones, planteles, partidos, partidoArbitros);
        var resumen = new SeedService(repos, supabase, contrasena, new TransactionTemplate(transacciones)).sembrar();
        log.info("[SEED] Listo: {} usuarios @prueba.local, {} torneos, {} inscripciones, {} miembros de plantel, {} partido(s).",
                resumen.usuarios(), resumen.torneos(), resumen.inscripciones(), resumen.miembrosDePlantel(), resumen.partidos());
        // Es una tarea de una sola vez: cierra la app al terminar.
        System.exit(SpringApplication.exit(contexto, () -> 0));
    }
}
