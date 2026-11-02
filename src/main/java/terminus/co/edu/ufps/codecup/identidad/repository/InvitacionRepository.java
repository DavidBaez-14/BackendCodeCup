package terminus.co.edu.ufps.codecup.identidad.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import terminus.co.edu.ufps.codecup.identidad.model.EstadoInvitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Invitacion;
import terminus.co.edu.ufps.codecup.identidad.model.Rol;

public interface InvitacionRepository extends JpaRepository<Invitacion, UUID> {

    Optional<Invitacion> findByToken(String token);

    List<Invitacion> findByCorreoAndEstado(String correo, EstadoInvitacion estado);

    boolean existsByCorreoAndRolAndEstado(String correo, Rol rol, EstadoInvitacion estado);

    List<Invitacion> findAllByOrderByCreatedAtDesc();

    List<Invitacion> findByEstadoOrderByCreatedAtDesc(EstadoInvitacion estado);
}
