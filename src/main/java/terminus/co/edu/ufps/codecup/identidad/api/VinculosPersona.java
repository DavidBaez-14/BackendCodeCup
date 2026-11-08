package terminus.co.edu.ufps.codecup.identidad.api;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoVinculo;
import terminus.co.edu.ufps.codecup.identidad.model.VinculoPersona;
import terminus.co.edu.ufps.codecup.identidad.repository.CuentaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.PersonaRepository;
import terminus.co.edu.ufps.codecup.identidad.repository.VinculoPersonaRepository;
import terminus.co.edu.ufps.codecup.shared.error.ApiException;

/**
 * Vinculación verificada (spec 0023): una cuenta pide quedarse con una persona que ya tiene
 * historial y que no tiene cuenta. Quién puede aprobarla y si la persona actual de la cuenta
 * tiene participaciones lo decide la competición, que conoce los planteles; aquí solo se
 * guarda la solicitud y se hace el cambio de persona.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VinculosPersona {

    private final VinculoPersonaRepository vinculoRepository;
    private final CuentaRepository cuentaRepository;
    private final PersonaRepository personaRepository;

    /** Lo que se muestra de una solicitud. */
    public record Resumen(UUID id, UUID cuentaId, UUID personaId, String estado, LocalDateTime creadoEn,
                          String solicitanteNombre, String solicitanteCorreo, UUID personaActualId) {
    }

    /**
     * Abre la solicitud de la cuenta hacia la persona. Si la cuenta ya tenía una pendiente, la
     * reemplaza: queda solo la última.
     */
    @Transactional
    public Resumen solicitar(UUID cuentaId, UUID personaId) {
        if (cuentaRepository.findByPersonaId(personaId).isPresent()) {
            throw ApiException.conflict("DOCUMENTO_REGISTRADO", "Esa persona ya tiene una cuenta.");
        }
        vinculoRepository.findFirstByCuentaIdAndEstado(cuentaId, EstadoVinculo.PENDIENTE).ifPresent(v -> {
            v.setEstado(EstadoVinculo.RECHAZADA);
            v.setMotivoRechazo("Reemplazada por una solicitud nueva.");
            v.setResueltoEn(LocalDateTime.now());
            vinculoRepository.saveAndFlush(v);
        });
        var vinculo = vinculoRepository.save(VinculoPersona.builder().cuentaId(cuentaId).personaId(personaId).build());
        log.info("[AUDIT][VINCULO_SOLICITADO] cuenta={} persona={}", cuentaId, personaId);
        return resumen(vinculo);
    }

    @Transactional(readOnly = true)
    public Optional<Resumen> pendienteDeCuenta(UUID cuentaId) {
        return vinculoRepository.findFirstByCuentaIdAndEstado(cuentaId, EstadoVinculo.PENDIENTE).map(this::resumen);
    }

    @Transactional(readOnly = true)
    public List<Resumen> pendientes() {
        return vinculoRepository.findByEstadoOrderByCreadoEnAsc(EstadoVinculo.PENDIENTE).stream().map(this::resumen).toList();
    }

    @Transactional(readOnly = true)
    public List<Resumen> pendientesHacia(Collection<UUID> personaIds) {
        if (personaIds.isEmpty()) return List.of();
        return vinculoRepository.findByPersonaIdInAndEstadoOrderByCreadoEnAsc(personaIds, EstadoVinculo.PENDIENTE)
                .stream().map(this::resumen).toList();
    }

    /** 404 si no existe; 409 si ya se resolvió. */
    @Transactional(readOnly = true)
    public Resumen requerirPendiente(UUID vinculoId) {
        var vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> ApiException.notFound("VINCULO_NO_ENCONTRADO", "Solicitud de vínculo no encontrada."));
        if (vinculo.getEstado() != EstadoVinculo.PENDIENTE) {
            throw ApiException.conflict("VINCULO_RESUELTO", "La solicitud ya fue resuelta: " + vinculo.getEstado() + ".");
        }
        return resumen(vinculo);
    }

    /**
     * La cuenta pasa a la persona con historial, que toma los nombres con que se registró, y la
     * persona anterior de la cuenta se borra. Quien llama ya comprobó que esa persona anterior
     * no tiene participaciones y movió o borró su perfil deportivo.
     */
    @Transactional
    public void aprobar(UUID vinculoId, UUID resolutorCuentaId) {
        var vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> ApiException.notFound("VINCULO_NO_ENCONTRADO", "Solicitud de vínculo no encontrada."));
        if (vinculo.getEstado() != EstadoVinculo.PENDIENTE) {
            throw ApiException.conflict("VINCULO_RESUELTO", "La solicitud ya fue resuelta: " + vinculo.getEstado() + ".");
        }
        if (cuentaRepository.findByPersonaId(vinculo.getPersonaId()).isPresent()) {
            throw ApiException.conflict("PERSONA_YA_VINCULADA", "Esa persona ya quedó vinculada a otra cuenta.");
        }
        var cuenta = cuentaRepository.findConPersonaByIdIn(List.of(vinculo.getCuentaId())).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("CUENTA_NO_ENCONTRADA", "Cuenta no encontrada."));
        var anterior = cuenta.getPersona();
        var destino = personaRepository.findById(vinculo.getPersonaId())
                .orElseThrow(() -> ApiException.notFound("PERSONA_NO_ENCONTRADA", "Persona no encontrada."));

        // El nombre lo decide la persona que se registró.
        if (anterior != null) {
            destino.setNombres(anterior.getNombres());
            destino.setApellidos(anterior.getApellidos());
        }
        personaRepository.save(destino);
        cuenta.setPersona(destino);
        cuentaRepository.saveAndFlush(cuenta);
        if (anterior != null && !anterior.getId().equals(destino.getId())) {
            personaRepository.delete(anterior);
        }

        vinculo.setEstado(EstadoVinculo.APROBADA);
        vinculo.setResueltoEn(LocalDateTime.now());
        vinculo.setResueltoPorCuentaId(resolutorCuentaId);
        vinculoRepository.save(vinculo);

        // Nadie más puede quedarse con esa persona.
        for (var otro : vinculoRepository.findByPersonaIdAndEstado(destino.getId(), EstadoVinculo.PENDIENTE)) {
            otro.setEstado(EstadoVinculo.RECHAZADA);
            otro.setMotivoRechazo("La persona ya quedó vinculada a otra cuenta.");
            otro.setResueltoEn(LocalDateTime.now());
            otro.setResueltoPorCuentaId(resolutorCuentaId);
            vinculoRepository.save(otro);
        }
        log.info("[AUDIT][VINCULO_APROBADO] vinculo={} cuenta={} persona={} por={}",
                vinculoId, cuenta.getId(), destino.getId(), resolutorCuentaId);
    }

    @Transactional
    public void rechazar(UUID vinculoId, UUID resolutorCuentaId, String motivo) {
        var vinculo = vinculoRepository.findById(vinculoId)
                .orElseThrow(() -> ApiException.notFound("VINCULO_NO_ENCONTRADO", "Solicitud de vínculo no encontrada."));
        if (vinculo.getEstado() != EstadoVinculo.PENDIENTE) {
            throw ApiException.conflict("VINCULO_RESUELTO", "La solicitud ya fue resuelta: " + vinculo.getEstado() + ".");
        }
        vinculo.setEstado(EstadoVinculo.RECHAZADA);
        vinculo.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        vinculo.setResueltoEn(LocalDateTime.now());
        vinculo.setResueltoPorCuentaId(resolutorCuentaId);
        vinculoRepository.save(vinculo);
        log.info("[AUDIT][VINCULO_RECHAZADO] vinculo={} por={}", vinculoId, resolutorCuentaId);
    }

    private Resumen resumen(VinculoPersona v) {
        var cuenta = cuentaRepository.findConPersonaByIdIn(List.of(v.getCuentaId())).stream().findFirst().orElse(null);
        var persona = cuenta != null ? cuenta.getPersona() : null;
        return new Resumen(v.getId(), v.getCuentaId(), v.getPersonaId(), v.getEstado().name(), v.getCreadoEn(),
                persona != null ? persona.nombreCompleto() : null,
                cuenta != null ? cuenta.getCorreo() : null,
                persona != null ? persona.getId() : null);
    }
}
