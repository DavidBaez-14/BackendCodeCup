package terminus.co.edu.ufps.codecup.finanzas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.api.DirectorioPersonas;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.model.EquipoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoInscripcion;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.finanzas.dto.ComprobanteDTO;
import terminus.co.edu.ufps.codecup.finanzas.dto.SubirComprobanteRequest;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.model.EstadoMulta;
import terminus.co.edu.ufps.codecup.finanzas.model.TipoComprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComprobanteService {

    private final ComprobanteRepository comprobanteRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final MultaRepository multaRepo;
    private final DirectorioPersonas directorioPersonas;
    private final terminus.co.edu.ufps.codecup.competicion.service.PermisosTorneo permisos;
    private final terminus.co.edu.ufps.codecup.competicion.service.ReglasInscripcion reglasInscripcion;

    @Transactional
    public ComprobanteDTO subir(SubirComprobanteRequest req, UUID personaUploader, UUID cuentaUploader) {
        TipoComprobante tipo;
        try {
            tipo = TipoComprobante.valueOf(req.getTipo());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Tipo de comprobante invalido: " + req.getTipo());
        }

        UUID equipoTorneoDelArchivo;
        if (tipo == TipoComprobante.INSCRIPCION) {
            var et = equipoTorneoRepo.findById(req.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("EquipoTorneo no encontrado."));
            if (!esDelegado(et, personaUploader)) {
                throw inscripcionAjena();
            }
            equipoTorneoDelArchivo = et.getId();
            // Solo se puede subir/reemplazar mientras la inscripcion siga pendiente de pago.
            if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
                throw new IllegalStateException(
                        "La inscripcion no admite comprobantes en su estado actual: "
                                + et.getEstadoInscripcion());
            }
        } else if (tipo == TipoComprobante.MULTA) {
            // Propiedad de la multa: solo el delegado del equipo sancionado puede subir su comprobante.
            var multa = multaRepo.findById(req.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));
            var et = equipoTorneoRepo.findById(multa.getEquipoTorneoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Equipo de la multa no encontrado."));
            if (!esDelegado(et, personaUploader)) {
                throw ApiException.forbidden("MULTA_AJENA", "No eres el delegado del equipo de esta multa.");
            }
            if (multa.getEstado() == EstadoMulta.PAGADA) {
                throw new IllegalStateException("La multa ya esta pagada.");
            }
            equipoTorneoDelArchivo = et.getId();
        } else {
            throw new RuntimeException("Tipo de comprobante no soportado: " + tipo);
        }

        // El archivo debe estar en la carpeta del propio equipo (la que entrega upload-url).
        // Si no, un delegado podria registrar la ruta del archivo de otro equipo y luego
        // descargarlo con download-url de su propio comprobante (spec 0001).
        if (!archivoEnCarpetaDe(req.getUrlArchivo(), equipoTorneoDelArchivo)) {
            throw ApiException.forbidden("ARCHIVO_AJENO",
                    "El archivo del comprobante no corresponde a tu equipo. Vuelve a subirlo.");
        }

        // Upsert: un comprobante por referencia. Si ya existe, se reemplaza (el delegado
        // corrige la foto) en vez de crear filas/archivos duplicados.
        Comprobante c = comprobanteRepo.findByReferenciaId(req.getReferenciaId())
                .stream().findFirst().orElseGet(Comprobante::new);
        c.setTipo(tipo);
        c.setReferenciaId(req.getReferenciaId());
        c.setUrlArchivo(req.getUrlArchivo());
        c.setSubidoPorCuentaId(cuentaUploader);
        // Reset del ciclo de revision. Se setean siempre porque con new Comprobante()
        // los @Builder.Default (estado/fechaSubida) NO se aplican.
        c.setEstado(EstadoComprobante.PENDIENTE_REVISION);
        c.setMotivoRechazo(null);
        c.setResueltoPorCuentaId(null);
        c.setFechaResolucion(null);
        c.setFechaSubida(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} subido/reemplazado por {} para referencia {}",
                c.getId(), cuentaUploader, c.getReferenciaId());
        return toDTO(c);
    }

    /**
     * Spec 0024: los comprobantes por revisar de los torneos que la cuenta administra, o de uno
     * solo con {@code torneoId} (403 NO_ADMIN_DEL_TORNEO si no lo administra). Sin torneos, vacía.
     */
    @Transactional(readOnly = true)
    public List<ComprobanteDTO> listarPendientes(UUID cuentaId, UUID torneoId) {
        java.util.Set<UUID> torneos;
        if (torneoId != null) {
            permisos.exigirTorneo(torneoId);
            if (!permisos.esAdmin(torneoId, cuentaId)) {
                throw ApiException.forbidden("NO_ADMIN_DEL_TORNEO", "No administras este torneo.");
            }
            torneos = java.util.Set.of(torneoId);
        } else {
            torneos = new java.util.HashSet<>(permisos.torneosQueAdministra(cuentaId));
        }
        if (torneos.isEmpty()) return List.of();
        return comprobanteRepo.findByEstado(EstadoComprobante.PENDIENTE_REVISION).stream()
                .filter(c -> torneos.contains(permisos.torneoDeComprobante(c)))
                .sorted(java.util.Comparator.comparing(Comprobante::getFechaSubida,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ComprobanteDTO> misComprobantes(UUID cuentaUploader) {
        return comprobanteRepo.findBySubidoPorCuentaIdOrderByFechaSubidaDesc(cuentaUploader)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public ComprobanteDTO aprobar(UUID comprobanteId, UUID cuentaAdmin) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        if (c.getEstado() != EstadoComprobante.PENDIENTE_REVISION) {
            throw new RuntimeException("Comprobante ya resuelto: " + c.getEstado());
        }

        // Resolver el objeto referenciado ANTES de marcar el comprobante. Si su estado ya
        // no permite aplicar el pago, lanzamos error en vez de aprobar "en silencio" un
        // comprobante que no surte efecto (todo dentro de la misma @Transactional).
        if (c.getTipo() == TipoComprobante.INSCRIPCION) {
            var et = equipoTorneoRepo.findById(c.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inscripcion del comprobante no encontrada."));
            if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
                throw new IllegalStateException(
                        "La inscripcion ya no esta pendiente de pago (estado: " + et.getEstadoInscripcion()
                                + "); no se puede aprobar este comprobante.");
            }
            reglasInscripcion.exigirCupo(et.getTorneo()); // spec 0029
            et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
            et.setAprobadoPorCuentaId(cuentaAdmin);
            equipoTorneoRepo.save(et);
            log.info("[FINANZAS] Inscripcion {} APROBADA por comprobante {}", et.getId(), c.getId());
        } else if (c.getTipo() == TipoComprobante.MULTA) {
            var multa = multaRepo.findById(c.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Multa del comprobante no encontrada."));
            if (multa.getEstado() == EstadoMulta.PAGADA) {
                throw new IllegalStateException("La multa ya estaba pagada; no se puede re-aprobar.");
            }
            multa.setEstado(EstadoMulta.PAGADA);
            multa.setPagadoPorCuentaId(c.getSubidoPorCuentaId());
            multa.setFechaPago(LocalDateTime.now());
            multaRepo.save(multa);
            log.info("[FINANZAS] Multa {} PAGADA por comprobante {}", multa.getId(), c.getId());
        }

        c.setEstado(EstadoComprobante.APROBADO);
        c.setResueltoPorCuentaId(cuentaAdmin);
        c.setFechaResolucion(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} APROBADO por {}", c.getId(), cuentaAdmin);
        return toDTO(c);
    }

    @Transactional
    public ComprobanteDTO rechazar(UUID comprobanteId, String motivo, UUID cuentaAdmin) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        if (c.getEstado() != EstadoComprobante.PENDIENTE_REVISION) {
            throw new RuntimeException("Comprobante ya resuelto: " + c.getEstado());
        }
        c.setEstado(EstadoComprobante.RECHAZADO);
        c.setMotivoRechazo(motivo);
        c.setResueltoPorCuentaId(cuentaAdmin);
        c.setFechaResolucion(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} RECHAZADO por {}: {}", c.getId(), cuentaAdmin, motivo);
        return toDTO(c);
    }

    /**
     * Spec 0001: antes de firmar una URL de subida, la inscripcion debe ser del delegado y
     * seguir pendiente de pago. La clave del archivo es fija por inscripcion y se sobrescribe,
     * asi que sin este control un delegado podria reemplazar el archivo de otro equipo, o el
     * suyo ya aprobado (la evidencia que reviso el admin).
     */
    @Transactional(readOnly = true)
    public void validarSubidaPropia(UUID equipoTorneoId, UUID personaDelegado) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!esDelegado(et, personaDelegado)) {
            throw inscripcionAjena();
        }
        if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
            throw ApiException.conflict("INSCRIPCION_SIN_PAGO_PENDIENTE",
                    "La inscripcion ya no admite comprobantes (estado: " + et.getEstadoInscripcion() + ").");
        }
    }

    /**
     * Spec 0001: un delegado descarga solo los de su equipo. Spec 0024: el admin, solo los de los
     * torneos que administra. La propiedad se resuelve por el objeto referenciado (inscripcion o
     * multa) y su delegado actual, no por quien lo subio.
     *
     * @return la clave del archivo en Storage
     */
    @Transactional(readOnly = true)
    public String archivoParaDescarga(UUID comprobanteId, UUID persona, UUID cuentaId) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        boolean esAdmin = permisos.esAdmin(permisos.torneoDeComprobante(c), cuentaId);
        if (!esAdmin && !esDelegado(equipoDelComprobante(c), persona)) {
            throw ApiException.forbidden("COMPROBANTE_AJENO", "Este comprobante no es de tu equipo.");
        }
        return c.getUrlArchivo();
    }

    private EquipoTorneo equipoDelComprobante(Comprobante c) {
        UUID equipoTorneoId = switch (c.getTipo()) {
            case INSCRIPCION -> c.getReferenciaId();
            case MULTA -> multaRepo.findById(c.getReferenciaId())
                    .map(m -> m.getEquipoTorneoId())
                    .orElse(null);
        };
        return equipoTorneoId == null ? null : equipoTorneoRepo.findById(equipoTorneoId).orElse(null);
    }

    private static boolean esDelegado(EquipoTorneo et, UUID persona) {
        return et != null && persona != null && persona.equals(et.getDelegadoPersonaId());
    }

    /** Solo claves dentro de la carpeta del equipo, sin saltos de ruta. */
    private static boolean archivoEnCarpetaDe(String urlArchivo, UUID equipoTorneoId) {
        return urlArchivo != null
                && urlArchivo.startsWith(equipoTorneoId + "/")
                && !urlArchivo.contains("..")
                && !urlArchivo.contains("\\");
    }

    private static ApiException inscripcionAjena() {
        return ApiException.forbidden("INSCRIPCION_AJENA", "No eres el delegado de esta inscripcion.");
    }

    private ComprobanteDTO toDTO(Comprobante c) {
        var et = equipoDelComprobante(c);
        var torneo = et != null ? et.getTorneo() : null;
        var equipo = et != null ? et.getEquipo() : null;
        return ComprobanteDTO.builder()
                .id(c.getId())
                .tipo(c.getTipo().name())
                .referenciaId(c.getReferenciaId())
                .torneoId(torneo != null ? torneo.getId() : null)
                .torneoNombre(torneo != null ? torneo.getNombre() : null)
                .equipoTorneoId(et != null ? et.getId() : null)
                .equipoNombre(equipo != null ? equipo.getNombre() : null)
                .urlArchivo(c.getUrlArchivo())
                .subidoPor(directorioPersonas.nombreDeCuenta(c.getSubidoPorCuentaId()))
                .estado(c.getEstado().name())
                .motivoRechazo(c.getMotivoRechazo())
                .aprobadoPor(directorioPersonas.nombreDeCuenta(c.getResueltoPorCuentaId()))
                .fechaSubida(c.getFechaSubida())
                .fechaResolucion(c.getFechaResolucion())
                .build();
    }
}
