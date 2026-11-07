package terminus.co.edu.ufps.codecup.finanzas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public ComprobanteDTO subir(SubirComprobanteRequest req, String cedulaUploader) {
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
            if (!esDelegado(et, cedulaUploader)) {
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
            if (!esDelegado(et, cedulaUploader)) {
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
        c.setCedulaUploader(cedulaUploader);
        // Reset del ciclo de revision. Se setean siempre porque con new Comprobante()
        // los @Builder.Default (estado/fechaSubida) NO se aplican.
        c.setEstado(EstadoComprobante.PENDIENTE_REVISION);
        c.setMotivoRechazo(null);
        c.setAprobadoPor(null);
        c.setFechaResolucion(null);
        c.setFechaSubida(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} subido/reemplazado por {} para referencia {}",
                c.getId(), cedulaUploader, c.getReferenciaId());
        return toDTO(c);
    }

    @Transactional(readOnly = true)
    public List<ComprobanteDTO> listarPendientes() {
        return comprobanteRepo.findByEstado(EstadoComprobante.PENDIENTE_REVISION)
                .stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<ComprobanteDTO> misComprobantes(String cedulaUploader) {
        return comprobanteRepo.findByCedulaUploaderOrderByFechaSubidaDesc(cedulaUploader)
                .stream().map(this::toDTO).toList();
    }

    @Transactional
    public ComprobanteDTO aprobar(UUID comprobanteId, String cedulaAdmin) {
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
            et.setEstadoInscripcion(EstadoInscripcion.APROBADO);
            et.setAprobadoPor(cedulaAdmin);
            equipoTorneoRepo.save(et);
            log.info("[FINANZAS] Inscripcion {} APROBADA por comprobante {}", et.getId(), c.getId());
        } else if (c.getTipo() == TipoComprobante.MULTA) {
            var multa = multaRepo.findById(c.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Multa del comprobante no encontrada."));
            if (multa.getEstado() == EstadoMulta.PAGADA) {
                throw new IllegalStateException("La multa ya estaba pagada; no se puede re-aprobar.");
            }
            multa.setEstado(EstadoMulta.PAGADA);
            multa.setPagadoPorCedula(c.getCedulaUploader());
            multa.setFechaPago(LocalDateTime.now());
            multaRepo.save(multa);
            log.info("[FINANZAS] Multa {} PAGADA por comprobante {}", multa.getId(), c.getId());
        }

        c.setEstado(EstadoComprobante.APROBADO);
        c.setAprobadoPor(cedulaAdmin);
        c.setFechaResolucion(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} APROBADO por {}", c.getId(), cedulaAdmin);
        return toDTO(c);
    }

    @Transactional
    public ComprobanteDTO rechazar(UUID comprobanteId, String motivo, String cedulaAdmin) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        if (c.getEstado() != EstadoComprobante.PENDIENTE_REVISION) {
            throw new RuntimeException("Comprobante ya resuelto: " + c.getEstado());
        }
        c.setEstado(EstadoComprobante.RECHAZADO);
        c.setMotivoRechazo(motivo);
        c.setAprobadoPor(cedulaAdmin);
        c.setFechaResolucion(LocalDateTime.now());
        comprobanteRepo.save(c);

        log.info("[FINANZAS] Comprobante {} RECHAZADO por {}: {}", c.getId(), cedulaAdmin, motivo);
        return toDTO(c);
    }

    /**
     * Spec 0001: antes de firmar una URL de subida, la inscripcion debe ser del delegado y
     * seguir pendiente de pago. La clave del archivo es fija por inscripcion y se sobrescribe,
     * asi que sin este control un delegado podria reemplazar el archivo de otro equipo, o el
     * suyo ya aprobado (la evidencia que reviso el admin).
     */
    @Transactional(readOnly = true)
    public void validarSubidaPropia(UUID equipoTorneoId, String cedulaDelegado) {
        var et = equipoTorneoRepo.findById(equipoTorneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
        if (!esDelegado(et, cedulaDelegado)) {
            throw inscripcionAjena();
        }
        if (et.getEstadoInscripcion() != EstadoInscripcion.PENDIENTE_PAGO) {
            throw ApiException.conflict("INSCRIPCION_SIN_PAGO_PENDIENTE",
                    "La inscripcion ya no admite comprobantes (estado: " + et.getEstadoInscripcion() + ").");
        }
    }

    /**
     * Spec 0001: el admin descarga cualquier comprobante; un delegado, solo los de su equipo.
     * La propiedad se resuelve por el objeto referenciado (inscripcion o multa) y su delegado
     * actual, no por quien lo subio.
     *
     * @return la clave del archivo en Storage
     */
    @Transactional(readOnly = true)
    public String archivoParaDescarga(UUID comprobanteId, String cedula, boolean esAdmin) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        if (!esAdmin && !esDelegado(equipoDelComprobante(c), cedula)) {
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

    private static boolean esDelegado(EquipoTorneo et, String cedula) {
        return et != null && cedula != null && cedula.equals(et.getDelegadoCedula());
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
        return ComprobanteDTO.builder()
                .id(c.getId())
                .tipo(c.getTipo().name())
                .referenciaId(c.getReferenciaId())
                .urlArchivo(c.getUrlArchivo())
                .cedulaUploader(c.getCedulaUploader())
                .estado(c.getEstado().name())
                .motivoRechazo(c.getMotivoRechazo())
                .aprobadoPor(c.getAprobadoPor())
                .fechaSubida(c.getFechaSubida())
                .fechaResolucion(c.getFechaResolucion())
                .build();
    }
}
