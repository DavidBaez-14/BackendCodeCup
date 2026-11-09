package terminus.co.edu.ufps.codecup.competicion.dto;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolicitudRequestDTO {
    private UUID equipoTorneoId;

    /** Spec 0029: el token del enlace, para pedir unirse en un torneo NO_LISTADO. */
    private String enlaceToken;
}
