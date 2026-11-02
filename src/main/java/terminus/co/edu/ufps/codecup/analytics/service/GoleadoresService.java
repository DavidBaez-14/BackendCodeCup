package terminus.co.edu.ufps.codecup.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.analytics.dto.GoleadorDTO;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotGoleadorRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoleadoresService {

    private final SnapshotGoleadorRepository repo;

    @Transactional(readOnly = true)
    public List<GoleadorDTO> obtener(UUID torneoId, int limit) {
        return repo.findByTorneoIdOrderByGolesDescJugadorNombreAsc(torneoId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(s -> GoleadorDTO.builder()
                        .posicion(s.getPosicion())
                        .cedula(s.getCedula())
                        .jugadorNombre(s.getJugadorNombre())
                        .equipoNombre(s.getEquipoNombre())
                        .goles(s.getGoles())
                        .build())
                .toList();
    }
}
