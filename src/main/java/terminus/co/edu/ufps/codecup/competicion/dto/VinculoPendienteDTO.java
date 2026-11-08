package terminus.co.edu.ufps.codecup.competicion.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/**
 * Una solicitud de vínculo vista por quien puede aprobarla (spec 0023): el delegado de un equipo
 * donde estuvo la persona, o un admin.
 */
@Data
@Builder
public class VinculoPendienteDTO {
    private UUID id;
    private LocalDateTime creadoEn;

    /** Quien pide el vínculo: su nombre y su correo de la cuenta. */
    private String solicitanteNombre;
    private String solicitanteCorreo;

    /** La persona del plantel que reclama. */
    private UUID personaId;
    private String personaNombre;
    private String personaNumeroDocumento;

    /** "Equipo (Torneo)" donde jugó esa persona. */
    private List<String> participaciones;
}
