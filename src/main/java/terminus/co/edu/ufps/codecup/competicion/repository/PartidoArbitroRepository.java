package terminus.co.edu.ufps.codecup.competicion.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import terminus.co.edu.ufps.codecup.competicion.model.PartidoArbitro;

public interface PartidoArbitroRepository extends JpaRepository<PartidoArbitro, UUID> {

    boolean existsByPartidoIdAndCuentaId(UUID partidoId, UUID cuentaId);

    Optional<PartidoArbitro> findByPartidoIdAndCuentaId(UUID partidoId, UUID cuentaId);

    List<PartidoArbitro> findByPartidoIdOrderByAsignadoEnAsc(UUID partidoId);

    List<PartidoArbitro> findByPartidoIdIn(Collection<UUID> partidoIds);

    List<PartidoArbitro> findByCuentaId(UUID cuentaId);

    /** Al quitarle el rol de árbitro en un torneo, se le quitan también sus partidos de ese torneo. */
    @Modifying
    @Query("DELETE FROM PartidoArbitro pa WHERE pa.cuentaId = :cuentaId "
            + "AND pa.partido.id IN (SELECT p.id FROM Partido p WHERE p.torneo.id = :torneoId)")
    void borrarDeCuentaEnTorneo(@Param("cuentaId") UUID cuentaId, @Param("torneoId") UUID torneoId);

    @Query("SELECT COUNT(pa) > 0 FROM PartidoArbitro pa WHERE pa.cuentaId = :cuentaId AND pa.partido.torneo.id = :torneoId")
    boolean asignadoEnTorneo(@Param("cuentaId") UUID cuentaId, @Param("torneoId") UUID torneoId);
}
