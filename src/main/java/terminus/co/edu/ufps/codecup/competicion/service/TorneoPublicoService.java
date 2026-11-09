package terminus.co.edu.ufps.codecup.competicion.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.PremioDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.DeporteDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.PaginaDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.TorneoPublicoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.publico.TorneoResumenPublicoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.Deporte;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoMembresia;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoPartido;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.Visibilidad;
import terminus.co.edu.ufps.codecup.competicion.repository.DeporteRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.JugadorEquipoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoArbitroRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Lecturas públicas (spec 0029, plan §6): Explorar y el detalle de un torneo. Toda decisión de
 * quién ve qué pasa por {@link VisibilidadService}. Nunca expone documentos ni correos: las
 * personas salen con su nombre visible (spec 0022).
 */
@Service
@RequiredArgsConstructor
public class TorneoPublicoService {

    private static final List<EstadoTorneo> LISTABLES =
            List.of(EstadoTorneo.PUBLICADO, EstadoTorneo.EN_CURSO, EstadoTorneo.FINALIZADO);
    private static final int TAMANO_MAXIMO = 50;

    private final TorneoRepository torneoRepo;
    private final DeporteRepository deporteRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final JugadorEquipoRepository jugadorEquipoRepo;
    private final PartidoRepository partidoRepo;
    private final TorneoRolRepository rolRepo;
    private final PartidoArbitroRepository partidoArbitroRepo;
    private final ComprobanteRepository comprobanteRepo;
    private final ClasificacionService clasificacionService;
    private final PremioService premioService;
    private final ArbitrajeService arbitrajeService;
    private final DirectorioPersonas directorioPersonas;
    private final VisibilidadService visibilidad;
    private final ReglasInscripcion reglasInscripcion;
    private final TorneoCreacionService torneoCreacionService;

    @Transactional(readOnly = true)
    public List<DeporteDTO> deportes() {
        return deporteRepo.findByActivoTrueOrderByOrdenAsc().stream().map(TorneoPublicoService::toDTO).toList();
    }

    /**
     * Explorar: solo los PUBLICO (no borradores). {@code q} busca en el nombre sin distinguir
     * mayúsculas ni tildes. Primero los de inscripciones abiertas, luego en curso, luego terminados;
     * dentro de cada grupo, los más nuevos.
     */
    @Transactional(readOnly = true)
    public PaginaDTO<TorneoResumenPublicoDTO> explorar(String deporte, String q, int pagina, int tamano) {
        int tam = Math.max(1, Math.min(tamano, TAMANO_MAXIMO));
        int pag = Math.max(0, pagina);
        String buscado = normalizar(q);
        var nombresDeporte = deporteRepo.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Deporte::getCodigo, Deporte::getNombre));
        var torneos = torneoRepo.findByVisibilidadAndEstadoIn(Visibilidad.PUBLICO, LISTABLES).stream()
                .filter(t -> deporte == null || deporte.isBlank() || deporte.equals(t.getDeporte()))
                .filter(t -> coincide(t.getNombre(), buscado))
                .sorted(Comparator.comparingInt((Torneo t) -> LISTABLES.indexOf(t.getEstado()))
                        .thenComparing(Torneo::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var contenido = torneos.stream()
                .skip((long) pag * tam)
                .limit(tam)
                .map(t -> new TorneoResumenPublicoDTO(t.getId(), t.getNombre(), t.getDeporte(),
                        nombresDeporte.get(t.getDeporte()),
                        t.getFormato() != null ? t.getFormato().name() : null, t.getEstado().name(),
                        t.getCupos(), reglasInscripcion.aprobadas(t), t.getCierreInscripciones(), t.getFechaInicio(),
                        organizador(t), inscripcionesAbiertas(t)))
                .toList();
        int totalPaginas = (int) Math.ceil(torneos.size() / (double) tam);
        return new PaginaDTO<>(contenido, pag, tam, torneos.size(), totalPaginas);
    }

    /** Por id: PUBLICO para cualquiera; NO_LISTADO y PRIVADO, solo participantes. Si no: 404. */
    @Transactional(readOnly = true)
    public TorneoPublicoDTO detalle(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        var quien = VisibilidadService.actual();
        visibilidad.exigirVisible(torneo, quien, false);
        return detalle(torneo, quien);
    }

    /** Por enlace: también NO_LISTADO. Un PRIVADO sigue siendo solo para participantes. */
    @Transactional(readOnly = true)
    public TorneoPublicoDTO porEnlace(String enlaceToken) {
        var torneo = torneoRepo.findByEnlaceToken(enlaceToken)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        var quien = VisibilidadService.actual();
        visibilidad.exigirVisible(torneo, quien, true);
        return detalle(torneo, quien);
    }

    private TorneoPublicoDTO detalle(Torneo t, VisibilidadService.Quien quien) {
        var deporte = deporteRepo.findById(t.getDeporte()).map(TorneoPublicoService::toDTO)
                .orElse(new DeporteDTO(t.getDeporte(), t.getDeporte(), "EQUIPO"));
        var aprobados = equipoTorneoRepo.findByTorneoIdAndEstadoInscripcionOrderByFechaInscripcionAsc(
                t.getId(), EstadoInscripcion.APROBADO);
        var relacion = miRelacion(t, quien);
        boolean administra = relacion != null && relacion.administra();
        BigDecimal valor = t.getMontoInscripcion();
        boolean cobra = valor != null && valor.compareTo(BigDecimal.ZERO) > 0;
        return TorneoPublicoDTO.builder()
                .id(t.getId())
                .nombre(t.getNombre())
                .edicion(t.getEdicion())
                .deporte(deporte)
                .formato(t.getFormato() != null ? t.getFormato().name() : null)
                .estado(t.getEstado().name())
                .visibilidad(t.getVisibilidad().name())
                .fechaInicio(t.getFechaInicio())
                .fechaFin(t.getFechaFin())
                .cierreInscripciones(t.getCierreInscripciones())
                .cupos(t.getCupos())
                .inscritos(aprobados.size())
                .inscripcionesAbiertas(inscripcionesAbiertas(t))
                .cobraInscripcion(cobra)
                .valorInscripcion(cobra ? valor : null)
                .exigeDocumento(Boolean.TRUE.equals(t.getExigeDocumento()))
                .reglas(t.getReglas())
                .organizador(organizador(t))
                .enlace(t.getVisibilidad() != Visibilidad.PRIVADO || administra ? torneoCreacionService.enlace(t) : null)
                .equipos(aprobados.stream()
                        .map(et -> new TorneoPublicoDTO.EquipoPublico(et.getId(), et.getEquipo().getNombre(), miembros(et)))
                        .toList())
                .tabla(clasificacionService.calcularPorGrupo(t.getId()))
                .partidos(partidoRepo.findByTorneoIdOrderByFechaAsc(t.getId()).stream()
                        .filter(p -> p.getEstado() != EstadoPartido.DESCANSO)
                        .map(this::toPublico)
                        .toList())
                .premios(premios(t.getId()))
                .miRelacion(relacion)
                .build();
    }

    private TorneoPublicoDTO.MiRelacion miRelacion(Torneo t, VisibilidadService.Quien quien) {
        if (quien == null || quien.anonimo()) return null;
        UUID torneoId = t.getId();
        boolean administra = rolRepo.existsByTorneoIdAndCuentaIdAndRol(torneoId, quien.cuentaId(), RolTorneo.ADMIN);
        boolean arbitra = rolRepo.existsByTorneoIdAndCuentaIdAndRol(torneoId, quien.cuentaId(), RolTorneo.ARBITRO)
                || partidoArbitroRepo.asignadoEnTorneo(quien.cuentaId(), torneoId);
        TorneoPublicoDTO.DelegadoDe delegadoDe = null;
        TorneoPublicoDTO.JuegaEn juegaEn = null;
        if (quien.personaId() != null) {
            delegadoDe = equipoTorneoRepo.findByTorneoIdAndDelegadoPersonaId(torneoId, quien.personaId())
                    .map(et -> new TorneoPublicoDTO.DelegadoDe(et.getId(), et.getEquipo().getNombre(),
                            et.getEstadoInscripcion().name(),
                            comprobanteRepo.findByReferenciaId(et.getId()).stream().findFirst()
                                    .map(c -> c.getEstado().name()).orElse(null)))
                    .orElse(null);
            juegaEn = jugadorEquipoRepo.findByPersonaIdAndTorneoId(quien.personaId(), torneoId)
                    .filter(je -> je.getEstado() == EstadoMembresia.ACTIVO)
                    .map(je -> new TorneoPublicoDTO.JuegaEn(je.getEquipoTorneo().getId(),
                            je.getEquipoTorneo().getEquipo().getNombre(),
                            je.getEquipoTorneo().getEstadoInscripcion().name()))
                    .orElse(null);
        }
        return new TorneoPublicoDTO.MiRelacion(administra, arbitra, delegadoDe, juegaEn);
    }

    private TorneoPublicoDTO.PartidoPublico toPublico(Partido p) {
        var d = arbitrajeService.toDTO(p, false);
        return new TorneoPublicoDTO.PartidoPublico(d.getPartidoId(), d.getFase(), d.getFaseCodigo(), d.getJornada(),
                d.getGrupo(),
                d.getLocal() == null ? null : new TorneoPublicoDTO.EquipoEnPartido(d.getLocal().equipoTorneoId(), d.getLocal().nombre()),
                d.getVisitante() == null ? null
                        : new TorneoPublicoDTO.EquipoEnPartido(d.getVisitante().equipoTorneoId(), d.getVisitante().nombre()),
                d.getFechaHora(), d.getLugar(), d.getEstado(),
                d.getMarcador() == null ? null : new TorneoPublicoDTO.Marcador(d.getMarcador().local(), d.getMarcador().visitante()));
    }

    private List<TorneoPublicoDTO.PremioPublico> premios(UUID torneoId) {
        List<PremioDTO> premios = premioService.listarPremios(torneoId);
        var personas = directorioPersonas.buscarVarias(premios.stream()
                .map(PremioDTO::getGanadorPersonaId).filter(Objects::nonNull).toList());
        return premios.stream()
                .map(p -> new TorneoPublicoDTO.PremioPublico(p.getId(), p.getCodigoCatalogo(), p.getNombreCatalogo(),
                        p.getTitulo(), p.getDescripcion(), p.getMonto(),
                        p.getGanadorPersonaId() != null && personas.containsKey(p.getGanadorPersonaId())
                                ? personas.get(p.getGanadorPersonaId()).nombreVisible()
                                : null,
                        p.getGanadorEquipoNombre()))
                .toList();
    }

    private int miembros(EquipoTorneo et) {
        return (int) jugadorEquipoRepo.findByEquipoTorneoIdOrderByFechaInicioAsc(et.getId()).stream()
                .filter(je -> je.getEstado() == EstadoMembresia.ACTIVO)
                .count();
    }

    private boolean inscripcionesAbiertas(Torneo t) {
        return reglasInscripcion.abiertas(t) && reglasInscripcion.hayCupo(t);
    }

    /** El nombre visible del primer admin (quien lo creó, salvo que ya no sea admin). */
    private String organizador(Torneo t) {
        var admins = rolRepo.findByTorneoIdAndRolOrderByCreadoEnAsc(t.getId(), RolTorneo.ADMIN);
        if (admins.isEmpty()) return null;
        UUID cuentaId = admins.get(0).getCuentaId();
        var cuenta = directorioPersonas.cuentas(List.of(cuentaId)).get(cuentaId);
        if (cuenta == null || cuenta.personaId() == null) return null;
        return directorioPersonas.buscar(cuenta.personaId()).map(p -> p.nombreVisible()).orElse(null);
    }

    private static DeporteDTO toDTO(Deporte d) {
        return new DeporteDTO(d.getCodigo(), d.getNombre(), d.getTipoCompetencia().name());
    }

    /** Cada palabra buscada aparece en el nombre, en cualquier orden ("nandu 2026" encuentra "Copa Ñandú 2026"). */
    static boolean coincide(String nombre, String buscado) {
        if (buscado == null) return true;
        String n = normalizar(nombre);
        if (n == null) return false;
        for (String palabra : buscado.split("\\s+")) {
            if (!n.contains(palabra)) return false;
        }
        return true;
    }

    /** Minúsculas y sin tildes; null si está vacío. */
    static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
