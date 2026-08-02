package dev.runwild;

/**
 * RunWild entry point.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code --version}  print the running JDK (contest verification)</li>
 *   <li>{@code --h3-demo}  prove HTTP/3 is genuinely negotiated</li>
 *   <li>{@code --plan}     print today's run recommendation to the terminal</li>
 *   <li>(no args)          serve the dashboard on http://localhost:8080</li>
 * </ul>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "--serve";

        switch (mode) {
            case "--version" -> printVersion();
            default -> {
                System.err.println("Unknown mode: " + mode);
                System.exit(2);
            }
        }
    }

    private static void printVersion() {
        var runtime = Runtime.version();
        System.out.printf("""
                RunWild 1.0.0
                Java runtime : %s
                Java vendor  : %s
                Feature ver  : %d
                Preview      : %s
                """,
                runtime,
                System.getProperty("java.vendor"),
                runtime.feature(),
                previewEnabled() ? "enabled" : "disabled");

        if (runtime.feature() < 26) {
            System.err.println("WARNING: RunWild targets Java 26; this runtime is older.");
        }
    }

    /** Preview features are on when the VM was started with --enable-preview. */
    private static boolean previewEnabled() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean()
                .getInputArguments().contains("--enable-preview");
    }
}
