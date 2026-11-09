package terminus.co.edu.ufps.codecup.identidad.api;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * "Tus secciones" de Inicio (spec 0026): lo que la cuenta organiza o arbitra, con lo urgente de
 * cada sección. Una sección en null significa que la cuenta no la tiene y la app no la muestra.
 *
 * @param creador si administra al menos un torneo
 * @param arbitro si es árbitro de al menos un torneo (aunque aún no tenga partidos)
 */
public record Secciones(Creador creador, Arbitro arbitro) {

    public static final Secciones NINGUNA = new Secciones(null, null);

    /**
     * @param torneos    torneos que administra
     * @param porRevisar inscripciones pendientes y comprobantes de multa por revisar
     */
    public record Creador(int torneos, int porRevisar) {
    }

    /**
     * @param partidosAsignados partidos por jugar que arbitra (programados, en curso o aplazados)
     * @param hoy               el próximo de hoy, o null
     */
    public record Arbitro(int partidosAsignados, PartidoDeHoy hoy) {
    }

    public record PartidoDeHoy(UUID partidoId, UUID torneoId, String torneo, String local, String visitante,
                               LocalDateTime fechaHora, String lugar, String estado) {
    }
}
