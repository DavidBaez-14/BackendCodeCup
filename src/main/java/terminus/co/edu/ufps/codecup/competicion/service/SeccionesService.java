package terminus.co.edu.ufps.codecup.competicion.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.competicion.dto.arbitro.ArbitroPartidoDTO;
import terminus.co.edu.ufps.codecup.competicion.model.RolTorneo;
import terminus.co.edu.ufps.codecup.competicion.repository.TorneoRolRepository;
import terminus.co.edu.ufps.codecup.identidad.api.ResumenSecciones;
import terminus.co.edu.ufps.codecup.identidad.api.Secciones;

/** "Tus secciones" para {@code GET /api/auth/me} (spec 0026, MOV-0004). */
@Service
@RequiredArgsConstructor
public class SeccionesService implements ResumenSecciones {

    private static final Set<String> POR_JUGAR = Set.of("PROGRAMADO", "EN_CURSO", "APLAZADO");

    private final CreadorService creadorService;
    private final ArbitrajeService arbitrajeService;
    private final TorneoRolRepository rolRepo;
    private final Clock reloj;

    @Override
    @Transactional(readOnly = true)
    public Secciones de(UUID cuentaId, UUID personaId) {
        var resumen = creadorService.resumen(cuentaId);
        Secciones.Creador creador = resumen.torneos() > 0
                ? new Secciones.Creador(resumen.torneos(), resumen.porRevisar())
                : null;

        Secciones.Arbitro arbitro = null;
        if (rolRepo.existsByCuentaIdAndRol(cuentaId, RolTorneo.ARBITRO)) {
            LocalDate hoy = LocalDate.now(reloj);
            var porJugar = arbitrajeService.misPartidos(cuentaId, null, null).stream()
                    .filter(p -> POR_JUGAR.contains(p.getEstado()))
                    .toList();
            var deHoy = porJugar.stream()
                    .filter(p -> p.getFechaHora() != null && p.getFechaHora().toLocalDate().equals(hoy))
                    .findFirst()
                    .map(SeccionesService::deHoy)
                    .orElse(null);
            arbitro = new Secciones.Arbitro(porJugar.size(), deHoy);
        }
        return new Secciones(creador, arbitro);
    }

    private static Secciones.PartidoDeHoy deHoy(ArbitroPartidoDTO p) {
        return new Secciones.PartidoDeHoy(p.getPartidoId(), p.getTorneoId(), p.getTorneo(),
                p.getLocal() != null ? p.getLocal().nombre() : null,
                p.getVisitante() != null ? p.getVisitante().nombre() : null,
                p.getFechaHora(), p.getLugar(), p.getEstado());
    }
}
