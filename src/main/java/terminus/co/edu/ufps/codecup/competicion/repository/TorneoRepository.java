package terminus.co.edu.ufps.codecup.competicion.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import terminus.co.edu.ufps.codecup.competicion.model.EstadoTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Torneo;

import java.util.List;
import java.util.UUID;

public interface TorneoRepository extends JpaRepository<Torneo, UUID> {
    List<Torneo> findAllByOrderByEdicionDesc();
    List<Torneo> findByEstadoInOrderByEdicionDesc(List<EstadoTorneo> estados);

    /** Spec 0029: los que aparecen en Explorar. */
    List<Torneo> findByVisibilidadAndEstadoIn(terminus.co.edu.ufps.codecup.competicion.model.Visibilidad visibilidad,
                                             java.util.Collection<EstadoTorneo> estados);

    java.util.Optional<Torneo> findByEnlaceToken(String enlaceToken);
}
