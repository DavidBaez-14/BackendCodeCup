package terminus.co.edu.ufps.codecup.competicion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.shared.error.ResourceNotFoundException;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.FinanzasConfigDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.InscripcionConfigDTO;
import terminus.co.edu.ufps.codecup.competicion.dto.admin.MultaConfigDTO;
import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.MultaConfig;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;
import terminus.co.edu.ufps.codecup.competicion.repository.MultaConfigRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoPremioRepository;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanzasConfigService {

    private final TorneoRepository torneoRepo;
    private final MultaConfigRepository multaConfigRepo;
    private final TorneoPremioRepository torneoPremioRepo;

    @Transactional(readOnly = true)
    public FinanzasConfigDTO obtenerConfig(UUID torneoId) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        var insc = InscripcionConfigDTO.builder()
                .monto(torneo.getMontoInscripcion())
                .cuentaDestino(torneo.getCuentaDestino())
                .bancoDestino(torneo.getBancoDestino())
                .titularCuenta(torneo.getTitularCuenta())
                .build();

        var multaConfig = multaConfigRepo.findByTorneoId(torneoId);
        var multas = multaConfig.map(m -> MultaConfigDTO.builder()
                .montoAmarilla(m.getMontoAmarilla())
                .montoAzul(m.getMontoAzul())
                .montoRoja(m.getMontoRoja())
                .fechasSuspensionRoja(m.getFechasSuspensionRoja())
                .build()).orElse(MultaConfigDTO.builder()
                .montoAmarilla(BigDecimal.ZERO)
                .montoAzul(BigDecimal.ZERO)
                .montoRoja(BigDecimal.ZERO)
                .fechasSuspensionRoja(1)
                .build());

        var premiosCount = torneoPremioRepo.existsByTorneoId(torneoId)
                ? torneoPremioRepo.findByTorneoId(torneoId).size()
                : 0L;

        return FinanzasConfigDTO.builder()
                .inscripcion(insc)
                .multas(multas)
                .premiosCount((long) premiosCount)
                .build();
    }

    @Transactional
    public InscripcionConfigDTO guardarInscripcionConfig(UUID torneoId, InscripcionConfigDTO dto) {
        var torneo = torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));
        validarNoFinalizado(torneo);

        torneo.setMontoInscripcion(dto.getMonto());
        torneo.setCuentaDestino(dto.getCuentaDestino());
        torneo.setBancoDestino(dto.getBancoDestino());
        torneo.setTitularCuenta(dto.getTitularCuenta());
        torneoRepo.save(torneo);

        return dto;
    }

    @Transactional
    public MultaConfigDTO guardarMultaConfig(UUID torneoId, MultaConfigDTO dto) {
        torneoRepo.findById(torneoId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado."));

        var config = multaConfigRepo.findByTorneoId(torneoId)
                .orElse(MultaConfig.builder()
                        .torneoId(torneoId)
                        .build());

        config.setMontoAmarilla(dto.getMontoAmarilla() != null ? dto.getMontoAmarilla() : BigDecimal.ZERO);
        config.setMontoAzul(dto.getMontoAzul() != null ? dto.getMontoAzul() : BigDecimal.ZERO);
        config.setMontoRoja(dto.getMontoRoja() != null ? dto.getMontoRoja() : BigDecimal.ZERO);
        config.setFechasSuspensionRoja(dto.getFechasSuspensionRoja() != null ? dto.getFechasSuspensionRoja() : 1);
        multaConfigRepo.save(config);

        return dto;
    }

    private void validarNoFinalizado(Torneo torneo) {
        if (torneo.getEstado() == EstadoTorneo.FINALIZADO) {
            throw new RuntimeException("El torneo esta FINALIZADO; la configuracion es inmutable.");
        }
    }
}
