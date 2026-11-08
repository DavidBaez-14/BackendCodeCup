package terminus.co.edu.ufps.codecup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
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
 * Supabase #1. Si una entidad y el esquema no coinciden, el contexto no levanta.
 *
 * <p>Spec 0022: antes de arrancar Spring, el esquema se migra solo hasta V4 y se cargan datos
 * con la forma vieja (cédulas). Al arrancar, Flyway aplica V5 y los tests revisan que esos
 * datos quedaron por persona.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "supabase.url=http://localhost:0",
        "app.bootstrap-admin-email="
})
class FlywayBaselineTest {

    // Datos de la forma vieja (V4) que la V5 debe convertir.
    private static final UUID P_DELEGADA = UUID.randomUUID();   // persona con cuenta, CC 1001
    private static final UUID P_ADMIN = UUID.randomUUID();      // persona con cuenta, CC 2002
    private static final UUID C_DELEGADA = UUID.randomUUID();
    private static final UUID C_ADMIN = UUID.randomUUID();
    private static final UUID TORNEO = UUID.randomUUID();
    private static final UUID TORNEO_2 = UUID.randomUUID();
    private static final UUID EQUIPO = UUID.randomUUID();
    private static final UUID INSCRIPCION = UUID.randomUUID();   // aprobada por el admin
    private static final UUID INSCRIPCION_2 = UUID.randomUUID(); // aprobada por el seed
    private static final UUID PARTIDO = UUID.randomUUID();
    private static final UUID MULTA = UUID.randomUUID();
    private static final UUID COMPROBANTE = UUID.randomUUID();
    private static final UUID SOLICITUD = UUID.randomUUID();

    private static final EmbeddedPostgres POSTGRES = iniciar();

    private static EmbeddedPostgres iniciar() {
        try {
            var pg = EmbeddedPostgres.builder().start();
            crearTablasViejasDePublic(pg);
            migrarHastaV4ConDatosViejos(pg);
            return pg;
        } catch (IOException | java.sql.SQLException e) {
            throw new IllegalStateException("No se pudo iniciar el Postgres embebido", e);
        }
    }

    /**
     * Spec 0021: Supabase #1 todavía tiene las tablas de AuthCodeCup en {@code public}.
     * Se recrean (forma mínima, con la FK real) antes de que Flyway migre, para probar que V3 las borra.
     */
    private static void crearTablasViejasDePublic(EmbeddedPostgres pg) throws java.sql.SQLException {
        try (var con = pg.getPostgresDatabase().getConnection(); var st = con.createStatement()) {
            st.execute("create table public.cuentas (id bigserial primary key, appwrite_user_id varchar(64), cedula varchar(20), correo varchar(255))");
            st.execute("create table public.cuenta_roles (id bigserial primary key, rol varchar(20), cuenta_id bigint references public.cuentas(id))");
            st.execute("create table public.jugadores (id bigserial primary key, cedula varchar(20), nombre varchar(255))");
        }
    }

    /** Spec 0022: el mundo de Supabase #1 antes de la V5, con cédulas en vez de personas. */
    private static void migrarHastaV4ConDatosViejos(EmbeddedPostgres pg) throws java.sql.SQLException {
        Flyway.configure()
                .dataSource(pg.getPostgresDatabase())
                .schemas("identidad", "competicion", "finanzas", "analytics")
                .locations("classpath:db/migration")
                .target("4")
                .load()
                .migrate();
        String sql = """
                insert into identidad.personas (id, nombres, apellidos, tipo_documento, numero_documento, created_at) values
                  ('%1$s', 'Ana', 'Registrada', 'CC', '1001', now()),
                  ('%2$s', 'Admin', 'Uno', 'CC', '2002', now());
                insert into identidad.cuentas (id, proveedor, proveedor_sub, correo, persona_id, estado, fecha_creacion) values
                  ('%3$s', 'SUPABASE', 'sub-ana', 'ana@prueba.local', '%1$s', 'ACTIVA', now()),
                  ('%4$s', 'SUPABASE', 'sub-admin', 'admin@prueba.local', '%2$s', 'ACTIVA', now());
                insert into competicion.jugadores (cedula, nombre, correo, altura_cm, posicion, created_at) values
                  ('1001', 'Ana Registrada', 'ana@prueba.local', 170, 'DEFENSA', now()),
                  ('3003', 'Beto SinCuenta', null, null, null, now());
                insert into competicion.torneos (id, nombre, edicion, estado, repechaje, created_at) values
                  ('%5$s', 'Copa vieja', 2026, 'EN_CURSO', false, now()),
                  ('%6$s', 'Copa vieja 2', 2026, 'EN_CURSO', false, now());
                insert into competicion.equipos (id, nombre, delegado_cedula, created_at) values
                  ('%7$s', 'Viejos FC', '1001', now());
                insert into competicion.equipo_torneo (id, torneo_id, equipo_id, delegado_cedula, estado_inscripcion, fecha_inscripcion, aprobado_por) values
                  ('%8$s', '%5$s', '%7$s', '1001', 'APROBADO', now(), '2002'),
                  ('%9$s', '%6$s', '%7$s', '1001', 'APROBADO', now(), 'seed');
                insert into competicion.jugador_equipo (torneo_id, equipo_torneo_id, cedula, estado, fecha_inicio, nombre_snapshot) values
                  ('%5$s', '%8$s', '1001', 'ACTIVO', current_date, 'Ana Registrada'),
                  ('%5$s', '%8$s', '3003', 'ACTIVO', current_date, 'Beto');
                insert into competicion.partidos (id, torneo_id, equipo_local_torneo_id, fecha, estado, created_at) values
                  ('%10$s', '%5$s', '%8$s', now(), 'FINALIZADO', now());
                insert into competicion.partido_jugador (partido_id, equipo_torneo_id, cedula, goles, jugo) values
                  ('%10$s', '%8$s', '3003', 1, true);
                insert into competicion.eventos_partido (partido_id, equipo_torneo_id, cedula, tipo_evento, orden, created_at) values
                  ('%10$s', '%8$s', '3003', 'GOL', 1, now()),
                  ('%10$s', '%8$s', null, 'GOL', 2, now());
                insert into competicion.solicitudes_equipo (id, torneo_id, equipo_torneo_id, cedula, estado, nombre, correo, fecha_solicitud) values
                  ('%13$s', '%5$s', '%8$s', '1001', 'PENDIENTE', 'Ana Registrada', 'ana@prueba.local', now());
                insert into competicion.premio_asignado (torneo_id, premio_id, cedula, fecha_asignacion) values
                  ('%5$s', gen_random_uuid(), '3003', now());
                insert into finanzas.multas (id, cedula, partido_id, equipo_torneo_id, torneo_id, tipo_sancion, monto, estado,
                                             partidos_suspension, habilitado_manual, fecha_generacion, pagado_por_cedula, habilitado_por_cedula) values
                  ('%11$s', '3003', '%10$s', '%8$s', '%5$s', 'AMARILLA', 5000, 'PAGADA', 0, false, now(), '2002', 'seed');
                insert into finanzas.comprobantes (id, referencia_id, cedula_uploader, tipo, estado, aprobado_por, url_archivo, fecha_subida) values
                  ('%12$s', '%8$s', '1001', 'INSCRIPCION', 'APROBADO', '2002', '%8$s/comprobante', now());
                insert into analytics.snapshot_goleador (torneo_id, cedula, goles, actualizado_en) values
                  ('%5$s', '3003', 1, now()),
                  ('%5$s', '9999', 1, now());
                """.formatted(P_DELEGADA, P_ADMIN, C_DELEGADA, C_ADMIN, TORNEO, TORNEO_2, EQUIPO,
                INSCRIPCION, INSCRIPCION_2, PARTIDO, MULTA, COMPROBANTE, SOLICITUD);
        try (var con = pg.getPostgresDatabase().getConnection(); var st = con.createStatement()) {
            st.execute(sql);
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
        assertThat(versiones).containsExactly("1", "2", "3", "4", "5", "6");

        var schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata where schema_name in ('identidad','competicion','finanzas','analytics')",
                String.class);
        assertThat(schemas).containsExactlyInAnyOrder("identidad", "competicion", "finanzas", "analytics");

        assertThat(jdbc.queryForObject("select count(*) from competicion.premio_catalogo", Integer.class)).isEqualTo(7);
    }

    /** Spec 0021: V3 borra las tablas viejas de AuthCodeCup en public. */
    @Test
    void lasTablasViejasDePublicYaNoExisten() {
        var quedan = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' "
                        + "and table_name in ('cuentas','cuenta_roles','jugadores')",
                String.class);
        assertThat(quedan).isEmpty();
    }

    @Test
    void documentoIncompletoLoRechazaLaBd() {
        var error = org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "insert into identidad.personas (nombres, numero_documento, created_at) values ('X', '123', now())"));
        assertThat(error).hasMessageContaining("personas_documento_completo_check");
    }

    /** Spec 0022: el número de documento no se repite aunque cambie el tipo (TI y CC comparten NUIP). */
    @Test
    void numeroDeDocumentoRepetidoConOtroTipo_loRechazaLaBd() {
        var error = catchThrowable(() -> jdbc.update(
                "insert into identidad.personas (nombres, tipo_documento, numero_documento, created_at) values ('X', 'TI', '1001', now())"));
        assertThat(error).hasMessageContaining("personas_numero_documento_uk");
    }

    /** Spec 0022, criterio 1: V5 convierte las cédulas en personas y cuentas, sin perder datos. */
    @Test
    void migraLosDatosDeCedulaAPersona() {
        // El miembro sin cuenta pasó a ser una persona sin cuenta, con tipo CC.
        var beto = jdbc.queryForMap(
                "select id, nombres, tipo_documento from identidad.personas where numero_documento = '3003'");
        UUID pBeto = (UUID) beto.get("id");
        assertThat(beto.get("nombres")).isEqualTo("Beto SinCuenta");
        assertThat(beto.get("tipo_documento")).isEqualTo("CC");
        assertThat(jdbc.queryForObject("select count(*) from identidad.cuentas where persona_id = ?", Integer.class, pBeto))
                .isZero();

        // Perfil deportivo solo para quien tenía datos.
        assertThat(jdbc.queryForMap("select altura_cm, posicion from competicion.perfil_deportivo where persona_id = ?", P_DELEGADA))
                .containsEntry("altura_cm", 170).containsEntry("posicion", "DEFENSA");
        assertThat(jdbc.queryForObject("select count(*) from competicion.perfil_deportivo where persona_id = ?", Integer.class, pBeto))
                .isZero();

        // Delegado y actores.
        assertThat(uuid("select delegado_persona_id from competicion.equipos where id = ?", EQUIPO)).isEqualTo(P_DELEGADA);
        assertThat(uuid("select delegado_persona_id from competicion.equipo_torneo where id = ?", INSCRIPCION)).isEqualTo(P_DELEGADA);
        assertThat(uuid("select aprobado_por_cuenta_id from competicion.equipo_torneo where id = ?", INSCRIPCION)).isEqualTo(C_ADMIN);
        assertThat(uuid("select aprobado_por_cuenta_id from competicion.equipo_torneo where id = ?", INSCRIPCION_2)).isNull();

        // Participantes.
        assertThat(jdbc.queryForList("select persona_id from competicion.jugador_equipo where equipo_torneo_id = ?",
                UUID.class, INSCRIPCION)).containsExactlyInAnyOrder(P_DELEGADA, pBeto);
        assertThat(uuid("select persona_id from competicion.partido_jugador where partido_id = ?", PARTIDO)).isEqualTo(pBeto);
        assertThat(jdbc.queryForList("select persona_id from competicion.eventos_partido where partido_id = ? order by orden",
                UUID.class, PARTIDO)).containsExactly(pBeto, null);
        assertThat(uuid("select persona_id from competicion.solicitudes_equipo where id = ?", SOLICITUD)).isEqualTo(P_DELEGADA);
        assertThat(uuid("select persona_id from competicion.premio_asignado where torneo_id = ?", TORNEO)).isEqualTo(pBeto);

        // Finanzas.
        assertThat(uuid("select persona_id from finanzas.multas where id = ?", MULTA)).isEqualTo(pBeto);
        assertThat(uuid("select pagado_por_cuenta_id from finanzas.multas where id = ?", MULTA)).isEqualTo(C_ADMIN);
        assertThat(uuid("select habilitado_por_cuenta_id from finanzas.multas where id = ?", MULTA)).isNull();
        assertThat(uuid("select subido_por_cuenta_id from finanzas.comprobantes where id = ?", COMPROBANTE)).isEqualTo(C_DELEGADA);
        assertThat(uuid("select resuelto_por_cuenta_id from finanzas.comprobantes where id = ?", COMPROBANTE)).isEqualTo(C_ADMIN);

        // El snapshot que no se pudo resolver se borró (se reconstruye).
        assertThat(jdbc.queryForList("select persona_id from analytics.snapshot_goleador where torneo_id = ?", UUID.class, TORNEO))
                .containsExactly(pBeto);

        // No queda ninguna cédula ni la tabla vieja.
        assertThat(jdbc.queryForList(
                "select table_schema || '.' || table_name || '.' || column_name from information_schema.columns "
                        + "where table_schema in ('identidad','competicion','finanzas','analytics') "
                        + "and (column_name like '%cedula%' or (table_name = 'solicitudes_equipo' and column_name in ('nombre','correo')))",
                String.class)).isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'competicion' and table_name = 'jugadores'",
                Integer.class)).isZero();
    }

    private UUID uuid(String sql, Object arg) {
        return jdbc.queryForObject(sql, UUID.class, arg);
    }

    /** Bug C5: V4 deja un premio por tipo en cada torneo. OTRO se repite, pero no con el mismo título. */
    @Test
    void premiosDuplicadosLosRechazaLaBd() {
        UUID torneo = torneoNuevo();
        insertarPremio(torneo, "CAMPEON", null);
        insertarPremio(torneo, "OTRO", "Juego limpio");
        insertarPremio(torneo, "OTRO", "Mejor barra");
        insertarPremio(torneoNuevo(), "CAMPEON", null);

        assertThat(catchThrowable(() -> insertarPremio(torneo, "CAMPEON", null)))
                .hasMessageContaining("torneo_premio_tipo_uk");
        assertThat(catchThrowable(() -> insertarPremio(torneo, "OTRO", "  JUEGO LIMPIO ")))
                .hasMessageContaining("torneo_premio_otro_titulo_uk");
    }

    private UUID torneoNuevo() {
        return jdbc.queryForObject(
                "insert into competicion.torneos (nombre, edicion, estado, repechaje, created_at) "
                        + "values ('Copa C5', 2026, 'BORRADOR', false, now()) returning id",
                UUID.class);
    }

    private void insertarPremio(UUID torneoId, String codigo, String titulo) {
        jdbc.update("insert into competicion.torneo_premio (torneo_id, premio_id, titulo, created_at) "
                        + "values (?, (select id from competicion.premio_catalogo where codigo = ?), ?, now())",
                torneoId, codigo, titulo);
    }
}
