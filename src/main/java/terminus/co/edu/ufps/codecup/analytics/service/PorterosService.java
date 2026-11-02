package terminus.co.edu.ufps.codecup.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.analytics.dto.PorteroDTO;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPorteroRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PorterosService {

    private final SnapshotPorteroRepository repo;

    @Transactional(readOnly = true)
    public List<PorteroDTO> obtener(UUID torneoId, int limit) {
        return repo.findByTorneoIdOrderByPromedioGcAscEquipoNombreAsc(torneoId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(s -> PorteroDTO.builder()
                        .posicion(s.getPosicion())
                        .equipoTorneoId(s.getEquipoTorneoId())
                        .equipoNombre(s.getEquipoNombre())
                        .golesEnContra(s.getGolesEnContra())
                        .partidosJugados(s.getPartidosJugados())
                        .promedio(s.getPromedioGc())
                        .build())
                .toList();
    }
}
