package terminus.co.edu.ufps.codecup.identidad.api;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import terminus.co.edu.ufps.codecup.identidad.dto.InvitacionDTO;
import terminus.co.edu.ufps.codecup.identidad.model.AmbitoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;
import terminus.co.edu.ufps.codecup.identidad.service.InvitacionService;

/**
 * Puerta de los otros módulos a las invitaciones con ámbito (spec 0024). La competición comprueba
 * primero que quien invita administra el torneo; aquí solo se guarda y se lista. Al aceptarse, la
 * competición recibe {@link InvitacionConAmbitoAceptada}.
 */
@Service
@RequiredArgsConstructor
public class Invitaciones {

    private final InvitacionService invitacionService;

    /** @param correo opcional: sin correo, la acepta la primera cuenta que abra el enlace */
    public InvitacionDTO aTorneo(UUID torneoId, String nombreTorneo, Rol rol, String correo, UUID creadorCuentaId) {
        return invitacionService.crearConAmbito(AmbitoInvitacion.TORNEO, torneoId, nombreTorneo, rol, correo,
                creadorCuentaId);
    }

    public List<InvitacionDTO> pendientesDeTorneo(UUID torneoId) {
        return invitacionService.pendientesDe(AmbitoInvitacion.TORNEO, torneoId);
    }

    public void revocarDeTorneo(UUID invitacionId, UUID torneoId) {
        invitacionService.revocarDe(invitacionId, AmbitoInvitacion.TORNEO, torneoId);
    }
}
