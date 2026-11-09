package terminus.co.edu.ufps.codecup.competicion.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.repository.EquipoTorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.PartidoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.finanzas.model.Comprobante;
import terminus.co.edu.ufps.codecup.finanzas.repository.ComprobanteRepository;
import terminus.co.edu.ufps.codecup.finanzas.repository.MultaRepository;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;

/**
 * Reglas de permiso por torneo (spec 0024), sin leer el contexto de seguridad: reciben la cuenta.
 * Las usan el bean {@code authz} (en {@code @PreAuthorize}) y los servicios que filtran listas
 * (bandejas de comprobantes, "mis torneos").
 *
 * <p>Los recursos que cuelgan de un torneo (inscripción, partido, multa, comprobante) se
 * resuelven primero a su torneo. Si no existen: 404.
 */
@Service
@RequiredArgsConstructor
public class PermisosTorneo {

    private final TorneoRepository torneoRepo;
    private final TorneoRolRepository rolRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;
    private final PartidoRepository partidoRepo;
    private final MultaRepository multaRepo;
    private final ComprobanteRepository comprobanteRepo;

    @Transactional(readOnly = true)
    public boolean esAdmin(UUID torneoId, UUID cuentaId) {
        return torneoId != null && cuentaId != null
                && rolRepo.existsByTorneoIdAndCuentaIdAndRol(torneoId, cuentaId, RolTorneo.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UUID> torneosQueAdministra(UUID cuentaId) {
        return rolRepo.torneosDe(cuentaId, RolTorneo.ADMIN);
    }

    /** 404 si el torneo no existe. */
    @Transactional(readOnly = true)
    public void exigirTorneo(UUID torneoId) {
        if (torneoId == null || !torneoRepo.existsById(torneoId)) {
            throw new ResourceNotFoundException("Torneo no encontrado.");
        }
    }

    @Transactional(readOnly = true)
    public UUID torneoDeInscripcion(UUID equipoTorneoId) {
        return equipoTorneoRepo.findById(equipoTorneoId)
                .map(et -> et.getTorneo().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Inscripcion no encontrada."));
    }

    @Transactional(readOnly = true)
    public UUID torneoDePartido(UUID partidoId) {
        return partidoRepo.findById(partidoId)
                .map(p -> p.getTorneo().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado."));
    }

    @Transactional(readOnly = true)
    public UUID torneoDeMulta(UUID multaId) {
        return multaRepo.findById(multaId)
                .map(m -> m.getTorneoId())
                .orElseThrow(() -> new ResourceNotFoundException("Multa no encontrada."));
    }

    @Transactional(readOnly = true)
    public UUID torneoDeComprobante(UUID comprobanteId) {
        var c = comprobanteRepo.findById(comprobanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Comprobante no encontrado."));
        UUID torneoId = torneoDeComprobante(c);
        if (torneoId == null) {
            throw new ResourceNotFoundException("El comprobante no apunta a una inscripcion o multa existente.");
        }
        return torneoId;
    }

    /** Torneo del objeto al que apunta el comprobante (inscripción o multa); null si ya no existe. */
    @Transactional(readOnly = true)
    public UUID torneoDeComprobante(Comprobante c) {
        return switch (c.getTipo()) {
            case INSCRIPCION -> equipoTorneoRepo.findById(c.getReferenciaId())
                    .map(et -> et.getTorneo().getId()).orElse(null);
            case MULTA -> multaRepo.findById(c.getReferenciaId()).map(m -> m.getTorneoId()).orElse(null);
        };
    }
}
