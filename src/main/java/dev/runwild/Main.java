package dev.runwild;

import dev.runwild.cli.Cli;
import dev.runwild.config.Config;
import dev.runwild.fetch.DataFetcher;
import dev.runwild.fetch.Http;

/**
 * RunWild entry point.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@code --plan}      print today's run recommendation (default)</li>
 *   <li>{@code --serve}     serve the dashboard on http://localhost:8080</li>
 *   <li>{@code --h3-demo}   prove HTTP/3 is genuinely negotiated</li>
 *   <li>{@code --benchmark} measure concurrent vs sequential fetching</li>
 *   <li>{@code --version}   print the running JDK (contest verification)</li>
 * </ul>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "--plan";

        switch (mode) {
            case "--version" -> printVersion();
            case "--h3-demo" -> Http.demo();
            case "--plan" -> plan();
            case "--benchmark" -> benchmark();
            case "--help", "-h" -> printHelp();
            default -> {
                System.err.println("Unknown mode: " + mode);
                printHelp();
                System.exit(2);
            }
        }
    }

    private static void plan() {
        try {
            Cli.render(new PlanService().build());
        } catch (Exception e) {
            System.err.println();
            System.err.println("  Could not build a plan: " + e.getMessage());
            System.err.println("  RunWild needs internet access to reach the weather and");
            System.err.println("  air-quality services. Check your connection and try again.");
            System.exit(1);
        }
    }

    /**
     * Quantifies what structured concurrency buys, so the README can quote a measurement
     * instead of an assertion.
     */
    private static void benchmark() throws Exception {
        Config config = Config.load();
        var fetcher = new DataFetcher();

        System.out.println("Warming up (TLS handshakes and DNS are one-time costs)...");
        fetcher.fetch(config);

        long concurrent = fetcher.fetch(config).telemetry().totalMillis();
        long sequential = fetcher.timeSequential(config);

        System.out.printf("""

                Three upstream sources, same data, same machine:
                  concurrent (StructuredTaskScope) : %d ms
                  sequential                       : %d ms
                  speed-up                         : %.1fx
                %n""", concurrent, sequential, sequential / (double) Math.max(1, concurrent));
    }

    private static void printHelp() {
        System.out.println("""
                RunWild — should I run outside?

                  --plan        today's recommendation (default)
                  --serve       dashboard at http://localhost:8080
                  --h3-demo     prove HTTP/3 is negotiated
                  --benchmark   concurrent vs sequential fetch timing
                  --version     runtime and Java version

                Configure your location and tolerances in runwild.properties.
                """);
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
