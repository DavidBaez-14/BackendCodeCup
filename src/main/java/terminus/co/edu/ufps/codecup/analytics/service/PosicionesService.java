package terminus.co.edu.ufps.codecup.analytics.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import terminus.co.edu.ufps.codecup.analytics.dto.PosicionDTO;
import terminus.co.edu.ufps.codecup.analytics.model.SnapshotPosicion;
import terminus.co.edu.ufps.codecup.analytics.repository.SnapshotPosicionRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PosicionesService {

    private final SnapshotPosicionRepository repo;

    @Transactional(readOnly = true)
    public Map<String, List<PosicionDTO>> obtenerPorGrupo(UUID torneoId) {
        List<SnapshotPosicion> all = repo.findByTorneoIdOrderByGrupoAscPosicionAsc(torneoId);
        Map<String, List<PosicionDTO>> resultado = new LinkedHashMap<>();
        for (SnapshotPosicion s : all) {
            resultado.computeIfAbsent(s.getGrupo(), k -> new ArrayList<>())
                    .add(toDTO(s));
        }
        return resultado;
    }

    private PosicionDTO toDTO(SnapshotPosicion s) {
        List<String> form = s.getForm() == null || s.getForm().isBlank()
                ? List.of()
                : Arrays.stream(s.getForm().split(","))
                        .map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
        return PosicionDTO.builder()
                .posicion(s.getPosicion())
                .equipoTorneoId(s.getEquipoTorneoId())
                .equipoNombre(s.getEquipoNombre())
                .grupo(s.getGrupo())
                .pts(s.getPts()).pj(s.getPj()).pg(s.getPg()).pe(s.getPe()).pp(s.getPp())
                .gf(s.getGf()).gc(s.getGc()).dg(s.getDg()).rojas(s.getRojas())
                .form(form)
                .descalificado(s.getDescalificado())
                .build();
    }
}
