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
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;
import terminus.co.edu.ufps.codecup.competicion.model.Equipo;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FormatoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.PerfilDeportivo;
import terminus.co.edu.ufps.codecup.competicion.model.JugadorEquipo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoArbitro;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.TorneoRol;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PerfilDeportivoRepository;
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
                               PerfilDeportivoRepository perfiles, TorneoRepository torneos,
                               TorneoRolRepository torneoRoles, EquipoRepository equipos,
                               EquipoTorneoRepository inscripciones, JugadorEquipoRepository planteles,
                               PartidoRepository partidos, PartidoArbitroRepository partidoArbitros) {
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

        if (u.perfil() != null && repos.perfiles().findById(persona.getId()).isEmpty()) {
            repos.perfiles().save(PerfilDeportivo.builder()
                    .personaId(persona.getId())
                    .alturaCm(u.perfil().alturaCm())
                    .piernaHabil(u.perfil().piernaHabil())
                    .posicion(u.perfil().posicion())
                    .build());
        }
    }

    /** Persona de un usuario de prueba (ya creada en asegurarCuenta). */
    private UUID personaDe(UsuarioPrueba u) {
        return repos.personas().findFirstByNumeroDocumento(u.documento())
                .orElseThrow(() -> new IllegalStateException("Falta la persona de " + u.alias()))
                .getId();
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

        // Spec 0024: no hay admins globales; admin01 administra los dos torneos de prueba.
        UUID admin = cuentaDe(porAlias.get("admin01"));
        asegurarRol(copa, admin, RolTorneo.ADMIN);
        asegurarRol(liga, admin, RolTorneo.ADMIN);

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
            UUID delegadoId = personaDe(delegado);
            // Spec 0026: un delegado puede tener varios equipos; el del seed se busca por nombre.
            Equipo equipo = repos.equipos().findByDelegadoPersonaIdOrderByCreatedAtDesc(delegadoId).stream()
                    .filter(e -> ep.nombre().equals(e.getNombre()))
                    .findFirst()
                    .orElseGet(() -> repos.equipos().save(Equipo.builder()
                            .nombre(ep.nombre())
                            .delegadoPersonaId(delegadoId)
                            .build()));

            EquipoTorneo enCopa = asegurarInscripcion(copa, equipo, delegadoId, estadosEnCopa[i]);
            miembros += asegurarPlantel(copa, enCopa, delegado, ep.plantel(), porAlias);

            if (i < 2) {
                EquipoTorneo enLaLiga = asegurarInscripcion(liga, equipo, delegadoId, EstadoInscripcion.APROBADO);
                miembros += asegurarPlantel(liga, enLaLiga, delegado, ep.plantel(), porAlias);
                enLiga.add(enLaLiga);
            }
        }
        Partido programado = asegurarPartido(liga, enLiga.get(0), enLiga.get(1));
        asegurarArbitros(liga, programado, porAlias);
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
                .deporte(terminus.co.edu.ufps.codecup.competicion.model.Deporte.FUTSAL)
                .visibilidad(terminus.co.edu.ufps.codecup.competicion.model.Visibilidad.PUBLICO)
                .cupos(8)
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

    private UUID cuentaDe(UsuarioPrueba u) {
        return repos.cuentas().findByPersonaId(personaDe(u))
                .orElseThrow(() -> new IllegalStateException("Falta la cuenta de " + u.alias()))
                .getId();
    }

    private TorneoRol asegurarRol(Torneo torneo, UUID cuentaId, RolTorneo rol) {
        return repos.torneoRoles().findByTorneoIdAndCuentaIdAndRol(torneo.getId(), cuentaId, rol)
                .orElseGet(() -> repos.torneoRoles().save(TorneoRol.builder()
                        .torneo(torneo)
                        .cuentaId(cuentaId)
                        .rol(rol)
                        .creadoEn(LocalDateTime.now())
                        .build()));
    }

    private EquipoTorneo asegurarInscripcion(Torneo torneo, Equipo equipo, UUID delegadoPersonaId,
                                             EstadoInscripcion estado) {
        return repos.inscripciones().findByTorneoIdAndEquipoId(torneo.getId(), equipo.getId())
                .orElseGet(() -> repos.inscripciones().save(EquipoTorneo.builder()
                        .torneo(torneo)
                        .equipo(equipo)
                        .delegadoPersonaId(delegadoPersonaId)
                        .estadoInscripcion(estado)
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
            UUID personaId = personaDe(m);
            if (repos.planteles().findByPersonaIdAndTorneoId(personaId, torneo.getId()).isPresent()) continue;
            var perfil = m.perfil();
            repos.planteles().save(JugadorEquipo.builder()
                    .personaId(personaId)
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
    private Partido asegurarPartido(Torneo liga, EquipoTorneo local, EquipoTorneo visitante) {
        List<Partido> entreEllos = repos.partidos().findByTorneoId(liga.getId()).stream()
                .filter(p -> p.getEquipoLocalTorneo() != null && p.getEquipoVisitanteTorneo() != null
                        && local.getId().equals(p.getEquipoLocalTorneo().getId())
                        && visitante.getId().equals(p.getEquipoVisitanteTorneo().getId()))
                .toList();
        var programado = entreEllos.stream().filter(p -> p.getEstado() == EstadoPartido.PROGRAMADO).findFirst();
        if (programado.isPresent()) return programado.get();
        int jornada = entreEllos.stream()
                .map(Partido::getJornada)
                .filter(j -> j != null)
                .max(Integer::compare)
                .orElse(0) + 1;
        return repos.partidos().save(Partido.builder()
                .torneo(liga)
                .equipoLocalTorneo(local)
                .equipoVisitanteTorneo(visitante)
                .fecha(LocalDate.now().plusDays(1).atTime(LocalTime.of(16, 0)))
                .lugar("Cancha 1 (datos de prueba)")
                .estado(EstadoPartido.PROGRAMADO)
                .fase(FaseTorneo.GRUPOS)
                .jornada(jornada)
                .build());
    }

    /**
     * Spec 0025: arbitro01 queda asignado al partido programado de la liga y arbitro02 arbitra todos
     * sus partidos con el permiso total. Así se prueban las dos formas de arbitrar.
     */
    private void asegurarArbitros(Torneo liga, Partido programado, Map<String, UsuarioPrueba> porAlias) {
        UUID arbitro01 = cuentaDe(porAlias.get("arbitro01"));
        UUID arbitro02 = cuentaDe(porAlias.get("arbitro02"));
        asegurarRol(liga, arbitro01, RolTorneo.ARBITRO);
        var rol02 = asegurarRol(liga, arbitro02, RolTorneo.ARBITRO);
        if (!Boolean.TRUE.equals(rol02.getTodosLosPartidos())) {
            rol02.setTodosLosPartidos(true);
            repos.torneoRoles().save(rol02);
        }
        if (!repos.partidoArbitros().existsByPartidoIdAndCuentaId(programado.getId(), arbitro01)) {
            repos.partidoArbitros().save(PartidoArbitro.builder()
                    .partido(programado)
                    .cuentaId(arbitro01)
                    .asignadoEn(LocalDateTime.now())
                    .build());
        }
    }
}
