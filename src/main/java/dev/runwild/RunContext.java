package dev.runwild;

import dev.runwild.config.Config;

/**
 * The configuration in force for the current request.
 *
 * <p>Held in a {@link ScopedValue} rather than passed as a parameter because the web
 * layer lets a caller override any tolerance per request (so two people can hit the same
 * server and get answers tuned to their own hay fever) while the CLI just uses the file.
 * A {@code ScopedValue} binds that choice for the dynamic extent of one request, is
 * immutable, and — unlike a {@code ThreadLocal} — costs nothing to inherit into the
 * virtual threads the server runs on and cannot leak between pooled requests.
 */
public final class RunContext {

    public static final ScopedValue<Config> CONFIG = ScopedValue.newInstance();

    private RunContext() {}

    /** The bound config, or the one on disk when running outside a bound scope. */
    public static Config current() {
        return CONFIG.isBound() ? CONFIG.get() : Config.load();
    }
}
