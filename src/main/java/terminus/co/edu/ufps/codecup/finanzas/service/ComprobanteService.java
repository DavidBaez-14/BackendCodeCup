package terminus.co.edu.ufps.codecup.finanzas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

        if (tipo == TipoComprobante.INSCRIPCION) {
            var et = equipoTorneoRepo.findById(req.getReferenciaId())
                    .orElseThrow(() -> new ResourceNotFoundException("EquipoTorneo no encontrado."));
            if (!et.getDelegadoCedula().equals(cedulaUploader)) {
                throw new RuntimeException("No eres el delegado de esta inscripcion.");
            }
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
            if (!et.getDelegadoCedula().equals(cedulaUploader)) {
                throw new RuntimeException("No eres el delegado del equipo de esta multa.");
            }
            if (multa.getEstado() == EstadoMulta.PAGADA) {
                throw new IllegalStateException("La multa ya esta pagada.");
            }
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

    @Transactional(readOnly = true)
    public ComprobanteDTO obtenerComprobante(UUID id) {
        return comprobanteRepo.findById(id)
                .map(this::toDTO)
                .orElse(null);
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
