package terminus.co.edu.ufps.codecup.seed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.support.TransactionTemplate;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FormatoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Jugador;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.identidad.model.Cuenta;
import terminus.co.edu.ufps.codecup.identidad.model.CuentaRol;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoCuenta;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoRol;
import terminus.co.edu.ufps.codecup.identidad.model.Persona;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.model.TipoDocumento;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRolRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;
import terminus.co.edu.ufps.codecup.seed.DatosDePrueba.EquipoPrueba;
import terminus.co.edu.ufps.codecup.seed.DatosDePrueba.UsuarioPrueba;

/**
 * Siembra los usuarios y datos de prueba (spec 0019). Idempotente: cada cosa se busca por su
 * llave natural (correo, documento, nombre de torneo, delegado, torneo + equipo) y solo se crea
 * si falta. Lo que ya existe no se pisa: si en una prueba se aprobó o bloqueó algo, se respeta.
 *
 * <p>No es un bean: lo arma {@link SeedRunner} (perfil seed) y los tests, con una Admin API simulada.
 */
public final class SeedService {

    /** Los repositorios que toca el seed. */
    public record Repositorios(PersonaRepository personas, CuentaRepository cuentas, CuentaRolRepository roles,
                               JugadorRepository jugadores, TorneoRepository torneos, EquipoRepository equipos,
                               EquipoTorneoRepository inscripciones, JugadorEquipoRepository planteles,
                               PartidoRepository partidos) {
    }

    public record Resumen(int usuarios, int torneos, int inscripciones, int miembrosDePlantel, int partidos) {
    }

    private static final String MOTIVO = "Datos de prueba (seed, spec 0019)";

    private final Repositorios repos;
    private final CuentasDeSupabase supabase;
    private final String contrasena;
    private final TransactionTemplate tx;

    public SeedService(Repositorios repos, CuentasDeSupabase supabase, String contrasena, TransactionTemplate tx) {
        this.repos = repos;
        this.supabase = supabase;
        this.contrasena = contrasena;
        this.tx = tx;
    }

    public Resumen sembrar() {
        if (contrasena == null || contrasena.isBlank()) {
            throw new IllegalStateException("Define SEED_PASSWORD en BackendCodeCup/.env antes de correr el seed.");
        }
        List<UsuarioPrueba> usuarios = DatosDePrueba.usuarios();
        for (UsuarioPrueba u : usuarios) {
            // La llamada a Supabase va fuera de la transacción de la BD.
            String sub = supabase.asegurarUsuario(u.correo(), contrasena, u.nombreCompleto());
            tx.executeWithoutResult(estado -> asegurarCuenta(u, sub));
        }
        return tx.execute(estado -> sembrarCompeticion(usuarios));
    }

    // ── Identidad ───────────────────────────────────────────────────────

    private void asegurarCuenta(UsuarioPrueba u, String sub) {
        Persona persona = repos.personas().findFirstByNumeroDocumento(u.documento())
                .orElseGet(() -> repos.personas().save(Persona.builder()
                        .nombres(u.nombres())
                        .apellidos(u.apellidos())
                        .tipoDocumento(TipoDocumento.CC)
                        .numeroDocumento(u.documento())
                        .build()));

        Cuenta cuenta = repos.cuentas().findConPersona(Cuenta.PROVEEDOR_SUPABASE, sub)
                .or(() -> repos.cuentas().findByPersonaId(persona.getId()))
                .orElseGet(() -> Cuenta.builder()
                        .proveedor(Cuenta.PROVEEDOR_SUPABASE)
                        .estado(EstadoCuenta.ACTIVA)
                        .fechaCreacion(LocalDateTime.now())
                        .build());
        // Si el usuario de Supabase se recreó, cambia el sub: la cuenta lo sigue.
        cuenta.setProveedorSub(sub);
        cuenta.setCorreo(u.correo());
        cuenta.setPersona(persona);
        Cuenta guardada = repos.cuentas().save(cuenta);

        for (Rol rol : u.roles()) {
            CuentaRol cuentaRol = repos.roles().findByCuentaIdAndRol(guardada.getId(), rol)
                    .orElseGet(() -> CuentaRol.builder()
                            .cuenta(guardada)
                            .rol(rol)
                            .fechaSolicitud(LocalDateTime.now())
                            .motivoSolicitud(MOTIVO)
                            .build());
            if (cuentaRol.getEstado() != EstadoRol.APROBADO) {
                cuentaRol.setEstado(EstadoRol.APROBADO);
                cuentaRol.setFechaResolucion(LocalDateTime.now());
                repos.roles().save(cuentaRol);
            }
        }

        if (u.perfil() != null && repos.jugadores().findById(u.documento()).isEmpty()) {
            repos.jugadores().save(Jugador.builder()
                    .cedula(u.documento())
                    .nombre(u.nombreCompleto())
                    .correo(u.correo())
                    .alturaCm(u.perfil().alturaCm())
                    .piernaHabil(u.perfil().piernaHabil())
                    .posicion(u.perfil().posicion())
                    .build());
        }
    }

    // ── Competición ─────────────────────────────────────────────────────

    private Resumen sembrarCompeticion(List<UsuarioPrueba> usuarios) {
        Map<String, UsuarioPrueba> porAlias = new LinkedHashMap<>();
        usuarios.forEach(u -> porAlias.put(u.alias(), u));

        // Copa PUBLICADA sin partidos: inscripciones, aprobaciones y fixture desde la web.
        Torneo copa = asegurarTorneo(DatosDePrueba.TORNEO_INSCRIPCIONES, EstadoTorneo.PUBLICADO,
                new BigDecimal("50000"), LocalDate.now().plusDays(7));
        // Liga EN_CURSO con un partido programado: el árbitro registra goles y lo cierra.
        Torneo liga = asegurarTorneo(DatosDePrueba.TORNEO_ARBITRO, EstadoTorneo.EN_CURSO,
                BigDecimal.ZERO, LocalDate.now().minusDays(1));

        EstadoInscripcion[] estadosEnCopa = {
            EstadoInscripcion.APROBADO, EstadoInscripcion.APROBADO,
            EstadoInscripcion.PENDIENTE_PAGO, EstadoInscripcion.PENDIENTE_PAGO,
        };
        List<EquipoPrueba> equipos = DatosDePrueba.equipos();
        List<EquipoTorneo> enLiga = new ArrayList<>();
        int miembros = 0;
        for (int i = 0; i < equipos.size(); i++) {
            EquipoPrueba ep = equipos.get(i);
            UsuarioPrueba delegado = porAlias.get(ep.aliasDelegado());
            Equipo equipo = repos.equipos().findByDelegadoCedula(delegado.documento())
                    .orElseGet(() -> repos.equipos().save(Equipo.builder()
                            .nombre(ep.nombre())
                            .delegadoCedula(delegado.documento())
                            .build()));

            EquipoTorneo enCopa = asegurarInscripcion(copa, equipo, delegado.documento(), estadosEnCopa[i]);
            miembros += asegurarPlantel(copa, enCopa, delegado, ep.plantel(), porAlias);

            if (i < 2) {
                EquipoTorneo enLaLiga = asegurarInscripcion(liga, equipo, delegado.documento(), EstadoInscripcion.APROBADO);
                miembros += asegurarPlantel(liga, enLaLiga, delegado, ep.plantel(), porAlias);
                enLiga.add(enLaLiga);
            }
        }
        asegurarPartido(liga, enLiga.get(0), enLiga.get(1));
        int partidos = repos.partidos().findByTorneoId(liga.getId()).size();

        return new Resumen(usuarios.size(), 2, equipos.size() + enLiga.size(), miembros, partidos);
    }

    private Torneo asegurarTorneo(String nombre, EstadoTorneo estado, BigDecimal monto, LocalDate inicio) {
        Optional<Torneo> existente = repos.torneos().findAll().stream()
                .filter(t -> nombre.equals(t.getNombre()))
                .findFirst();
        return existente.orElseGet(() -> repos.torneos().save(Torneo.builder()
                .nombre(nombre)
                .edicion(2026)
                .estado(estado)
                .formato(FormatoTorneo.LIGA)
                .repechaje(false)
                .configuradoEn(LocalDateTime.now())
                .publicadoEn(LocalDateTime.now())
                .fechaInicio(inicio)
                .fechaFin(inicio.plusDays(60))
                .montoInscripcion(monto)
                .cuentaDestino("000-123456-78")
                .bancoDestino("Banco de Prueba")
                .titularCuenta("CodeCup (datos de prueba)")
                .build()));
    }

    private EquipoTorneo asegurarInscripcion(Torneo torneo, Equipo equipo, String delegadoCedula,
                                             EstadoInscripcion estado) {
        return repos.inscripciones().findByTorneoIdAndEquipoId(torneo.getId(), equipo.getId())
                .orElseGet(() -> repos.inscripciones().save(EquipoTorneo.builder()
                        .torneo(torneo)
                        .equipo(equipo)
                        .delegadoCedula(delegadoCedula)
                        .estadoInscripcion(estado)
                        .aprobadoPor(estado == EstadoInscripcion.APROBADO ? "seed" : null)
                        .build()));
    }

    /** El delegado y su plantel, con camiseta y snapshots. Devuelve cuántos miembros tiene el plantel. */
    private int asegurarPlantel(Torneo torneo, EquipoTorneo inscripcion, UsuarioPrueba delegado,
                                List<String> plantel, Map<String, UsuarioPrueba> porAlias) {
        List<UsuarioPrueba> miembros = new ArrayList<>();
        miembros.add(delegado);
        plantel.forEach(alias -> miembros.add(porAlias.get(alias)));

        Map<String, Integer> camisetas = new HashMap<>();
        for (int k = 0; k < miembros.size(); k++) camisetas.put(miembros.get(k).documento(), k + 1);

        for (UsuarioPrueba m : miembros) {
            if (repos.planteles().findByCedulaAndTorneoId(m.documento(), torneo.getId()).isPresent()) continue;
            var perfil = m.perfil();
            repos.planteles().save(JugadorEquipo.builder()
                    .cedula(m.documento())
                    .torneo(torneo)
                    .equipoTorneo(inscripcion)
                    .fechaInicio(LocalDate.now())
                    .estado(EstadoMembresia.ACTIVO)
                    .numeroCamiseta(camisetas.get(m.documento()))
                    .nombreSnapshot(m.nombreCompleto())
                    .alturaCmSnapshot(perfil != null ? perfil.alturaCm() : null)
                    .piernaHabilSnapshot(perfil != null ? perfil.piernaHabil() : null)
                    .posicionSnapshot(perfil != null ? perfil.posicion() : null)
                    .snapshotAt(LocalDateTime.now())
                    .build());
        }
        return miembros.size();
    }

    /**
     * Garantiza un partido PROGRAMADO entre los dos equipos de la liga. Si el árbitro ya cerró
     * el anterior, crea el de la jornada siguiente; si hay uno programado, no hace nada.
     */
    private void asegurarPartido(Torneo liga, EquipoTorneo local, EquipoTorneo visitante) {
        List<Partido> entreEllos = repos.partidos().findByTorneoId(liga.getId()).stream()
                .filter(p -> p.getEquipoLocalTorneo() != null && p.getEquipoVisitanteTorneo() != null
                        && local.getId().equals(p.getEquipoLocalTorneo().getId())
                        && visitante.getId().equals(p.getEquipoVisitanteTorneo().getId()))
                .toList();
        if (entreEllos.stream().anyMatch(p -> p.getEstado() == EstadoPartido.PROGRAMADO)) return;
        int jornada = entreEllos.stream()
                .map(Partido::getJornada)
                .filter(j -> j != null)
                .max(Integer::compare)
                .orElse(0) + 1;
        repos.partidos().save(Partido.builder()
                .torneo(liga)
                .equipoLocalTorneo(local)
                .equipoVisitanteTorneo(visitante)
                .fecha(LocalDate.now().plusDays(1).atTime(LocalTime.of(16, 0)))
                .estado(EstadoPartido.PROGRAMADO)
                .fase(FaseTorneo.GRUPOS)
                .jornada(jornada)
                .build());
    }
}
