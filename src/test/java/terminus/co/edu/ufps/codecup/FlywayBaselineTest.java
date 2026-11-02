package terminus.co.edu.ufps.codecup;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spec 0017, criterio 17. Corre las migraciones de Flyway sobre un PostgreSQL real
 * embebido (sin Docker) y arranca Hibernate con ddl-auto=validate, igual que contra
 * Supabase #1. Si una entidad y V1 no coinciden, el contexto no levanta.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "supabase.url=http://localhost:0",
        "app.bootstrap-admin-email="
})
class FlywayBaselineTest {

    private static final EmbeddedPostgres POSTGRES = iniciar();

    private static EmbeddedPostgres iniciar() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar el Postgres embebido", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void detener() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migraYValidaContraPostgres() {
        var versiones = jdbc.queryForList(
                "select version from identidad.flyway_schema_history where success and version is not null order by installed_rank",
                String.class);
        assertThat(versiones).containsExactly("1", "2");

        var schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata where schema_name in ('identidad','competicion','finanzas','analytics')",
                String.class);
        assertThat(schemas).containsExactlyInAnyOrder("identidad", "competicion", "finanzas", "analytics");

        assertThat(jdbc.queryForObject("select count(*) from competicion.premio_catalogo", Integer.class)).isEqualTo(7);
    }

    @Test
    void documentoIncompletoLoRechazaLaBd() {
        var error = org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "insert into identidad.personas (nombres, numero_documento, created_at) values ('X', '123', now())"));
        assertThat(error).hasMessageContaining("personas_documento_completo_check");
    }
}
