package terminus.co.edu.ufps.codecup.seed;

/**
 * Lo único que el seed necesita de Supabase Auth: tener el usuario creado y confirmado.
 * Aparte del cliente real para poder simularlo en los tests.
 */
public interface CuentasDeSupabase {

    /**
     * Crea el usuario ya confirmado, sin enviar ningún correo. Si ya existe, le pone esta
     * contraseña y lo marca confirmado. Devuelve su id de Supabase (el {@code sub} del token).
     */
    String asegurarUsuario(String correo, String contrasena, String nombreVisible);
}
