package terminus.co.edu.ufps.codecup.competicion.service;

import terminus.co.edu.ufps.codecup.competicion.model.FaseTorneo;
import terminus.co.edu.ufps.codecup.competicion.model.Partido;

/** Textos para mostrar un partido sin que cada cliente traduzca los códigos (spec 0025). */
public final class EtiquetasPartido {

    private EtiquetasPartido() {
    }

    /** "Grupo A · Fecha 3", "Fecha 2" (liga), "Semifinal", "Final"… o null si no tiene fase. */
    public static String fase(Partido p) {
        FaseTorneo fase = p.getFase();
        if (fase == null) return null;
        return switch (fase) {
            case GRUPOS -> {
                String fecha = p.getJornada() != null ? "Fecha " + p.getJornada() : null;
                if (p.getGrupo() == null) yield fecha;
                yield fecha == null ? "Grupo " + p.getGrupo() : "Grupo " + p.getGrupo() + " · " + fecha;
            }
            case REPECHAJE -> "Repechaje";
            case OCTAVOS -> "Octavos de final";
            case CUARTOS -> "Cuartos de final";
            case SEMIS -> "Semifinal";
            case FINAL -> "Final";
            case TERCER_PUESTO -> "Tercer puesto";
        };
    }
}
