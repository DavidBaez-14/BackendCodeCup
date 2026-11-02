package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.*;
import terminus.co.edu.ufps.codecup.competicion.model.*;
import terminus.co.edu.ufps.codecup.competicion.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PremioService {

    private final TorneoRepository torneoRepo;
    private final PremioCatalogoRepository premioCatalogoRepo;
    private final TorneoPremioRepository torneoPremioRepo;
    private final PremioAsignadoRepository premioAsignadoRepo;
    private final EquipoTorneoRepository equipoTorneoRepo;

    @Transactional(readOnly = true)
    public List<PremioDTO> listarPremios(UUID torneoId) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        return torneoPremioRepo.findByTorneoId(torneoId).stream()
                .map(this::toPremioDTO)
                .toList();
    }

    @Transactional
    public PremioDTO crearPremio(UUID torneoId, CrearPremioRequest req) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        validarNoFinalizado(torneo);

        var catalogo = premioCatalogoRepo.findByCodigo(req.getCodigoCatalogo())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Codigo de premio invalido: " + req.getCodigoCatalogo()));

        if ("OTRO".equals(req.getCodigoCatalogo()) && (req.getTitulo() == null || req.getTitulo().isBlank())) {
            throw new RuntimeException("El campo titulo es obligatorio para premios OTRO.");
        }

        var tp = TorneoPremio.builder()
                .torneoId(torneoId)
                .premioId(catalogo.getId())
                .titulo(req.getTitulo())
                .descripcion(req.getDescripcion())
                .monto(req.getMonto())
                .build();
        torneoPremioRepo.save(tp);

        return toPremioDTO(tp);
    }

    @Transactional
    public PremioDTO actualizarPremio(UUID torneoId, UUID torneoPremioId, ActualizarPremioRequest req) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        validarNoFinalizado(torneo);

        var tp = torneoPremioRepo.findById(torneoPremioId)
                .orElseThrow(() -> new ResourceNotFoundException("Premio no encontrado."));
        if (!tp.getTorneoId().equals(torneoId)) {
            throw new RuntimeException("El premio no pertenece a este torneo.");
        }

        if (req.getTitulo() != null) tp.setTitulo(req.getTitulo());
        if (req.getDescripcion() != null) tp.setDescripcion(req.getDescripcion());
        if (req.getMonto() != null) tp.setMonto(req.getMonto());
        torneoPremioRepo.save(tp);

        return toPremioDTO(tp);
    }

    @Transactional
    public void eliminarPremio(UUID torneoId, UUID torneoPremioId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        validarNoFinalizado(torneo);

        var tp = torneoPremioRepo.findById(torneoPremioId)
                .orElseThrow(() -> new ResourceNotFoundException("Premio no encontrado."));
        if (!tp.getTorneoId().equals(torneoId)) {
            throw new RuntimeException("El premio no pertenece a este torneo.");
        }

        if (premioAsignadoRepo.existsByPremioId(torneoPremioId)) {
            throw new RuntimeException("No se puede eliminar un premio que ya tiene una asignacion. Quita la asignacion primero.");
        }

        torneoPremioRepo.delete(tp);
    }

    @Transactional
    public PremioAsignado asignarPremio(UUID torneoId, UUID torneoPremioId, AsignarPremioRequest req) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        var tp = torneoPremioRepo.findById(torneoPremioId)
                .orElseThrow(() -> new ResourceNotFoundException("Premio no encontrado."));
        if (!tp.getTorneoId().equals(torneoId)) {
            throw new RuntimeException("El premio no pertenece a este torneo.");
        }

        if (req.getCedula() == null && req.getEquipoTorneoId() == null) {
            throw new RuntimeException("Debe especificar cedula (individual) o equipoTorneoId (colectivo).");
        }

        var asignacion = premioAsignadoRepo.findByTorneoIdAndPremioId(torneoId, torneoPremioId)
                .orElse(PremioAsignado.builder()
                        .torneoId(torneoId)
                        .premioId(torneoPremioId)
                        .build());

        asignacion.setCedula(req.getCedula());
        asignacion.setEquipoTorneoId(req.getEquipoTorneoId());
        asignacion.setFechaAsignacion(LocalDateTime.now());
        premioAsignadoRepo.save(asignacion);

        return asignacion;
    }

    @Transactional
    public void quitarAsignacion(UUID torneoId, UUID torneoPremioId) {
        var asignacion = premioAsignadoRepo.findByTorneoIdAndPremioId(torneoId, torneoPremioId)
                .orElseThrow(() -> new ResourceNotFoundException("No hay asignacion para este premio."));
        premioAsignadoRepo.delete(asignacion);
    }

    @Transactional(readOnly = true)
    public List<PremioDTO> listarPremiosPublicos(UUID torneoId) {
        return listarPremios(torneoId);
    }

    private PremioDTO toPremioDTO(TorneoPremio tp) {
        var catalogo = premioCatalogoRepo.findById(tp.getPremioId()).orElse(null);
        var asignacion = premioAsignadoRepo.findByTorneoIdAndPremioId(tp.getTorneoId(), tp.getId()).orElse(null);

        String equipoNombre = null;
        if (asignacion != null && asignacion.getEquipoTorneoId() != null) {
            equipoNombre = equipoTorneoRepo.findById(asignacion.getEquipoTorneoId())
                    .map(et -> et.getEquipo().getNombre())
                    .orElse(null);
        }

        return PremioDTO.builder()
                .id(tp.getId())
                .premioId(tp.getPremioId())
                .codigoCatalogo(catalogo != null ? catalogo.getCodigo() : null)
                .nombreCatalogo(catalogo != null ? catalogo.getNombre() : null)
                .titulo(tp.getTitulo())
                .descripcion(tp.getDescripcion())
                .monto(tp.getMonto())
                .ganadorCedula(asignacion != null ? asignacion.getCedula() : null)
                .ganadorEquipoTorneoId(asignacion != null ? asignacion.getEquipoTorneoId() : null)
                .ganadorEquipoNombre(equipoNombre)
                .asignadoEn(asignacion != null ? asignacion.getFechaAsignacion() : null)
                .build();
    }

    private void validarNoFinalizado(Torneo torneo) {
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            throw new RuntimeException("El torneo esta FINALIZADO; los premios son inmutables.");
        }
    }
}
