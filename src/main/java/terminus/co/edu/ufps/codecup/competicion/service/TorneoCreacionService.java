package terminus.co.edu.ufps.codecup.competicion.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.TorneoDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.ActualizarDatosTorneoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.CrearTorneoRapidoRequest;
import terminus.co.edu.ufps.codecup.competicion.dto.creador.TorneoCreadoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.FormatoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.model.Visibilidad;
import terminus.co.edu.ufps.codecup.competicion.repository.DeporteRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Crear un torneo en un paso y editar sus datos básicos (spec 0029). El torneo nace con las
 * inscripciones abiertas y una configuración del formato por defecto, que el organizador ajusta
 * después en Configuración mientras no haya fixture.
 */
@Slf4j
@Service
public class TorneoCreacionService {

    /** Formatos que se ofrecen al crear. CHAMPIONS se configura después. */
    private static final List<FormatoTorneo> FORMATOS_RAPIDOS =
            List.of(FormatoTorneo.LIGA, FormatoTorneo.ELIMINACION_DIRECTA, FormatoTorneo.GRUPOS_ELIMINATORIAS);

    private final TorneoRepository torneoRepo;
    private final DeporteRepository deporteRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final RolesTorneoService rolesTorneoService;
    private final ReglasInscripcion reglasInscripcion;
    private final TorneoAdminService torneoAdminService;
    private final Clock reloj;
    private final String frontendUrl;

    public TorneoCreacionService(TorneoRepository torneoRepo, DeporteRepository deporteRepo,
                                 EquipoTorneoRepository equipoTorneoRepo, RolesTorneoService rolesTorneoService,
                                 ReglasInscripcion reglasInscripcion, TorneoAdminService torneoAdminService, Clock reloj,
                                 @Value("${app.frontend-urls}") List<String> frontendUrls) {
        this.torneoRepo = torneoRepo;
        this.deporteRepo = deporteRepo;
        this.equipoTorneoRepo = equipoTorneoRepo;
        this.rolesTorneoService = rolesTorneoService;
        this.reglasInscripcion = reglasInscripcion;
        this.torneoAdminService = torneoAdminService;
        this.reloj = reloj;
        this.frontendUrl = frontendUrls.isEmpty() ? "" : frontendUrls.get(0).trim();
    }

    @Transactional
    public TorneoCreadoDTO crearRapido(CrearTorneoRapidoRequest req, UUID creadorCuentaId) {
        String nombre = nombreValido(req.getNombre());
        String deporte = deporteValido(req.getDeporte());
        FormatoTorneo formato = formatoValido(req.getFormato());
        int cupos = cuposValidos(req.getCupos(), formato);
        LocalDate hoy = LocalDate.now(reloj);
        if (req.getFechaInicio() == null) {
            throw ApiException.badRequest("FECHA_INICIO_REQUERIDA", "Elige la fecha de inicio del torneo.");
        }
        if (req.getFechaInicio().isBefore(hoy)) {
            throw ApiException.badRequest("FECHA_INICIO_PASADA", "La fecha de inicio no puede ser anterior a hoy.");
        }
        if (req.getCierreInscripciones() != null
                && (req.getCierreInscripciones().isBefore(hoy) || req.getCierreInscripciones().isAfter(req.getFechaInicio()))) {
            throw ApiException.badRequest("CIERRE_INSCRIPCIONES_INVALIDO",
                    "El cierre de inscripciones va entre hoy y la fecha de inicio.");
        }
        Visibilidad visibilidad = visibilidadValida(req.getVisibilidad());
        BigDecimal valor = null;
        if (Boolean.TRUE.equals(req.getCobraInscripcion())) {
            if (req.getValorInscripcion() == null || req.getValorInscripcion().compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("VALOR_INSCRIPCION_INVALIDO", "Escribe el valor de la inscripción (mayor que cero).");
            }
            valor = req.getValorInscripcion();
        }

        var torneo = Torneo.builder()
                .nombre(nombre)
                .edicion(req.getFechaInicio().getYear())
                .deporte(deporte)
                .visibilidad(visibilidad)
                .cupos(cupos)
                .fechaInicio(req.getFechaInicio())
                .cierreInscripciones(req.getCierreInscripciones())
                .montoInscripcion(valor)
                .exigeDocumento(Boolean.TRUE.equals(req.getExigeDocumento()))
                .reglas(textoOpcional(req.getReglas(), 2000, "REGLAS_INVALIDAS"))
                .estado(EstadoTorneo.PUBLICADO)
                .publicadoEn(LocalDateTime.now())
                .repechaje(false)
                .build();
        configurarFormatoPorDefecto(torneo, formato, cupos);
        torneoRepo.save(torneo);
        rolesTorneoService.registrarCreador(torneo, creadorCuentaId);

        return TorneoCreadoDTO.builder()
                .id(torneo.getId())
                .nombre(torneo.getNombre())
                .deporte(torneo.getDeporte())
                .formato(torneo.getFormato().name())
                .estado(torneo.getEstado().name())
                .visibilidad(torneo.getVisibilidad().name())
                .cupos(torneo.getCupos())
                .fechaInicio(torneo.getFechaInicio())
                .cierreInscripciones(torneo.getCierreInscripciones())
                .valorInscripcion(torneo.getMontoInscripcion())
                .enlaceToken(torneo.getEnlaceToken())
                .enlace(enlace(torneo))
                .build();
    }

    /** Datos básicos del torneo. 409 TORNEO_FINALIZADO si ya terminó. */
    @Transactional
    public TorneoDTO actualizarDatos(UUID torneoId, ActualizarDatosTorneoRequest req) {
        var torneo = torneoRepo.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            throw ApiException.conflict("TORNEO_FINALIZADO", "El torneo ya terminó: sus datos no se cambian.");
        }
        if (req.getNombre() != null) torneo.setNombre(nombreValido(req.getNombre()));
        if (req.getDeporte() != null && !req.getDeporte().equals(torneo.getDeporte())) {
            String deporte = deporteValido(req.getDeporte());
            boolean hayInscripciones = equipoTorneoRepo.findByTorneoIdOrderByFechaInscripcionAsc(torneoId).stream()
                    .anyMatch(et -> et.getEstadoInscripcion() != EstadoInscripcion.RECHAZADO);
            if (hayInscripciones) {
                throw ApiException.conflict("DEPORTE_BLOQUEADO", "El torneo ya tiene inscripciones: no se puede cambiar el deporte.");
            }
            torneo.setDeporte(deporte);
        }
        if (req.getVisibilidad() != null) torneo.setVisibilidad(visibilidadValida(req.getVisibilidad()));
        if (Boolean.TRUE.equals(req.getSinLimiteDeCupos())) {
            torneo.setCupos(null);
        } else if (req.getCupos() != null) {
            if (req.getCupos() < 2 || req.getCupos() > 64) {
                throw ApiException.badRequest("CUPOS_FUERA_DE_RANGO", "Los cupos van de 2 a 64.");
            }
            int aprobadas = reglasInscripcion.aprobadas(torneo);
            if (req.getCupos() < aprobadas) {
                throw ApiException.conflict("CUPOS_MENOR_QUE_INSCRITOS",
                        "Ya hay " + aprobadas + " inscripciones aprobadas: los cupos no pueden ser menos.");
            }
            torneo.setCupos(req.getCupos());
        }
        if (req.getFechaInicio() != null) torneo.setFechaInicio(req.getFechaInicio());
        if (req.getFechaFin() != null) torneo.setFechaFin(req.getFechaFin());
        if (torneo.getFechaInicio() != null && torneo.getFechaFin() != null
                && torneo.getFechaFin().isBefore(torneo.getFechaInicio())) {
            throw ApiException.badRequest("FECHAS_INVALIDAS", "La fecha de fin no puede ser anterior a la de inicio.");
        }
        if (Boolean.TRUE.equals(req.getSinCierre())) {
            torneo.setCierreInscripciones(null);
        } else if (req.getCierreInscripciones() != null) {
            torneo.setCierreInscripciones(req.getCierreInscripciones());
        }
        if (req.getReglas() != null) torneo.setReglas(textoOpcional(req.getReglas(), 2000, "REGLAS_INVALIDAS"));
        torneoRepo.save(torneo);
        return torneoAdminService.toDTO(torneo);
    }

    /** Un enlace nuevo: el anterior deja de funcionar (para revocar un enlace filtrado). */
    @Transactional
    public TorneoCreadoDTO regenerarEnlace(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId).orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        torneo.setEnlaceToken(Torneo.nuevoEnlaceToken());
        torneoRepo.save(torneo);
        log.info("[AUDIT][ENLACE_REGENERADO] torneo={}", torneoId);
        return TorneoCreadoDTO.builder().id(torneo.getId()).nombre(torneo.getNombre())
                .visibilidad(torneo.getVisibilidad().name())
                .enlaceToken(torneo.getEnlaceToken()).enlace(enlace(torneo)).build();
    }

    /** El enlace para compartir: la página del torneo en la web, que también abre la app. */
    public String enlace(Torneo t) {
        return frontendUrl + "/t/" + t.getEnlaceToken();
    }

    // ── Formato por defecto ─────────────────────────────────────────────

    /**
     * Liga: nada más. Eliminación directa: las rondas que caben en los cupos (hasta octavos).
     * Grupos y eliminatorias: 2 grupos hasta 8 cupos y 4 grupos desde 9, clasifican 2 por grupo.
     */
    static void configurarFormatoPorDefecto(Torneo t, FormatoTorneo formato, int cupos) {
        t.setFormato(formato);
        t.setConfiguradoEn(LocalDateTime.now());
        switch (formato) {
            case LIGA -> {
                t.setNumGrupos(null);
                t.setClasificanPorGrupo(null);
                t.setRondasPlayoff(null);
            }
            case ELIMINACION_DIRECTA -> t.setRondasPlayoff(rondasPara(cupos));
            case GRUPOS_ELIMINATORIAS -> {
                int grupos = cupos <= 8 ? 2 : 4;
                t.setNumGrupos(grupos);
                t.setClasificanPorGrupo(2);
                t.setRondasPlayoff(rondasPara(grupos * 2));
            }
            case CHAMPIONS -> throw new IllegalArgumentException("CHAMPIONS no se crea en un paso");
        }
    }

    /** Rondas eliminatorias para {@code equipos} participantes: 2 → FINAL; 3-4 → SEMIS, FINAL… */
    static String rondasPara(int equipos) {
        if (equipos <= 2) return FaseTorneo.FINAL.name();
        if (equipos <= 4) return String.join(",", FaseTorneo.SEMIS.name(), FaseTorneo.FINAL.name());
        if (equipos <= 8) return String.join(",", FaseTorneo.CUARTOS.name(), FaseTorneo.SEMIS.name(), FaseTorneo.FINAL.name());
        return String.join(",", FaseTorneo.OCTAVOS.name(), FaseTorneo.CUARTOS.name(), FaseTorneo.SEMIS.name(),
                FaseTorneo.FINAL.name());
    }

    // ── Validaciones ────────────────────────────────────────────────────

    private static String nombreValido(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() < 3 || n.length() > 150) {
            throw ApiException.badRequest("NOMBRE_INVALIDO", "El nombre del torneo va de 3 a 150 caracteres.");
        }
        return n;
    }

    private String deporteValido(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw ApiException.badRequest("DEPORTE_INEXISTENTE", "Elige el deporte del torneo.");
        }
        return deporteRepo.findById(codigo.trim())
                .filter(d -> Boolean.TRUE.equals(d.getActivo()))
                .orElseThrow(() -> ApiException.badRequest("DEPORTE_INEXISTENTE", "Ese deporte no está en el catálogo."))
                .getCodigo();
    }

    private static FormatoTorneo formatoValido(String formato) {
        try {
            var f = FormatoTorneo.valueOf(formato == null ? "" : formato.trim());
            if (FORMATOS_RAPIDOS.contains(f)) return f;
        } catch (IllegalArgumentException e) {
            // cae al error de abajo
        }
        throw ApiException.badRequest("FORMATO_INVALIDO", "Elige Liga, Eliminación directa o Grupos y eliminatorias.");
    }

    private static int cuposValidos(Integer cupos, FormatoTorneo formato) {
        int minimo = formato == FormatoTorneo.GRUPOS_ELIMINATORIAS ? 4 : 2;
        int maximo = formato == FormatoTorneo.ELIMINACION_DIRECTA ? 16 : 64;
        if (cupos == null || cupos < minimo || cupos > maximo) {
            throw ApiException.badRequest("CUPOS_FUERA_DE_RANGO",
                    "Para este formato los cupos van de " + minimo + " a " + maximo + ".");
        }
        return cupos;
    }

    private static Visibilidad visibilidadValida(String visibilidad) {
        try {
            return Visibilidad.valueOf(visibilidad == null ? "" : visibilidad.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VISIBILIDAD_INVALIDA", "Elige Público, Solo con enlace o Privado.");
        }
    }

    private static String textoOpcional(String texto, int maximo, String codigo) {
        if (texto == null) return null;
        String t = texto.trim();
        if (t.length() > maximo) {
            throw ApiException.badRequest(codigo, "El texto no puede tener más de " + maximo + " caracteres.");
        }
        return t.isEmpty() ? null : t;
    }
}
