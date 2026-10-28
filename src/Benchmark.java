import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Analise experimental (Etapa 3). Gera tres arquivos CSV com as medicoes brutas:
 *
 *   resultados/tempos.csv    tempo de cada algoritmo para tamanhos crescentes (10 execucoes por tamanho)
 *   resultados/limiar.csv    Etapa 2: tempo da ordenacao inicial e da fase recursiva para varios limiares
 *   resultados/cruzamento.csv  forca bruta x divisao e conquista "pura" para entradas pequenas
 *
 * Cada execucao usa uma entrada aleatoria nova (semente fixa, entao e reproduzivel). Quando uma
 * chamada e rapida demais para o relogio, ela e repetida ate somar pelo menos MIN_BATCH_NS e o
 * tempo registrado e a media por chamada (coluna "repeticoes" no CSV).
 *
 * uso: java -cp out Benchmark [tempos|limiar|cruzamento|tudo]
 */
public final class Benchmark {

    static final int RUNS = 10;
    static final long MIN_BATCH_NS = 2_000_000L;
    static final int[] BASE_SIZES = {100, 200, 400, 800, 1600, 3200, 6400};
    static long sink; // impede que o JIT descarte as chamadas

    public static void main(String[] args) throws IOException {
        String what = args.length > 0 ? args[0] : "tudo";
        Files.createDirectories(Path.of("resultados"));
        System.out.println(machineInfo());
        if (what.equals("limiar") || what.equals("tudo")) threshold();
        if (what.equals("cruzamento") || what.equals("tudo")) crossover();
        if (what.equals("tempos") || what.equals("tudo")) times();
        System.out.println("sink=" + sink);
    }

    // ------------------------------------------------------------------ tempos.csv

    static void times() throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of("resultados/tempos.csv")))) {
            out.println("etapa,algoritmo,n,execucao,repeticoes,tempo_ns");
            Random rnd = new Random(1);
            warmUp(rnd);

            // Etapa 1: tamanhos do enunciado + tamanhos maiores para ver melhor a tendencia
            for (int n : sizes(100, 51_200)) {
                for (int run = 1; run <= RUNS; run++) {
                    int[] a = randomArray(rnd, n);
                    record(out, 1, "forca_bruta", n, run, () -> MaxSubarray.bruteForce(a).sum());
                    record(out, 1, "divisao_conquista", n, run, () -> MaxSubarray.divideAndConquer(a).sum());
                    record(out, 1, "kadane", n, run, () -> MaxSubarray.kadane(a).sum());
                }
                System.out.println("etapa1 n=" + n);
            }
            for (int n : sizes(102_400, 1_638_400)) {
                for (int run = 1; run <= RUNS; run++) {
                    int[] a = randomArray(rnd, n);
                    record(out, 1, "divisao_conquista", n, run, () -> MaxSubarray.divideAndConquer(a).sum());
                    record(out, 1, "kadane", n, run, () -> MaxSubarray.kadane(a).sum());
                }
                System.out.println("etapa1 n=" + n);
            }

            // Etapa 2
            for (int n : sizes(100, 25_600)) {
                for (int run = 1; run <= RUNS; run++) {
                    double[][] p = randomPoints(rnd, n);
                    record(out, 2, "forca_bruta", n, run, () -> ClosestPair.bruteForce(p[0], p[1]).i());
                    record(out, 2, "divisao_conquista", n, run, () -> ClosestPair.divideAndConquer(p[0], p[1]).i());
                }
                System.out.println("etapa2 n=" + n);
            }
            for (int n : sizes(51_200, 1_638_400)) {
                for (int run = 1; run <= RUNS; run++) {
                    double[][] p = randomPoints(rnd, n);
                    record(out, 2, "divisao_conquista", n, run, () -> ClosestPair.divideAndConquer(p[0], p[1]).i());
                }
                System.out.println("etapa2 n=" + n);
            }
        }
    }

    // ------------------------------------------------------------------ limiar.csv

    static void threshold() throws IOException {
        int[] thresholds = {3, 4, 6, 8, 12, 16, 24, 32, 48, 64, 96, 128, 256};
        int[] ns = {1_000, 10_000, 100_000, 1_000_000};
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of("resultados/limiar.csv")))) {
            out.println("n,limiar,fase,execucao,repeticoes,tempo_ns");
            Random rnd = new Random(2);
            warmUp(rnd);
            for (int n : ns) {
                // o efeito do limiar e pequeno perto do ruido, entao aqui usamos o dobro de execucoes
                for (int run = 1; run <= 2 * RUNS; run++) {
                    double[][] p = randomPoints(rnd, n);
                    // a ordenacao inicial nao depende do limiar: e medida uma vez por execucao
                    long[] ms = measure(() -> ClosestPair.presort(p[0], p[1]).px()[0]);
                    out.printf(Locale.ROOT, "%d,0,ordenacao,%d,%d,%d%n", n, run, ms[0], ms[1]);
                    ClosestPair.Sorted ps = ClosestPair.presort(p[0], p[1]);
                    for (int t : thresholds) {
                        long[] m = measure(() -> ClosestPair.solveSorted(ps, t).i());
                        out.printf(Locale.ROOT, "%d,%d,recursao,%d,%d,%d%n", n, t, run, m[0], m[1]);
                    }
                }
                System.out.println("limiar n=" + n);
            }
        }
    }

    // ------------------------------------------------------------------ cruzamento.csv

    /** Entradas pequenas: em que tamanho a divisao e conquista passa a vencer a forca bruta? */
    static void crossover() throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of("resultados/cruzamento.csv")))) {
            out.println("etapa,algoritmo,n,execucao,repeticoes,tempo_ns");
            Random rnd = new Random(3);
            warmUp(rnd);
            int[] ns = {2, 4, 8, 12, 16, 24, 32, 48, 64, 96, 128, 192, 256, 384, 512, 768, 1024};
            for (int n : ns) {
                for (int run = 1; run <= RUNS; run++) {
                    int[] a = randomArray(rnd, n);
                    record(out, 1, "forca_bruta", n, run, () -> MaxSubarray.bruteForce(a).sum());
                    record(out, 1, "divisao_conquista", n, run, () -> MaxSubarray.divideAndConquer(a).sum());
                    double[][] p = randomPoints(rnd, n);
                    record(out, 2, "forca_bruta", n, run, () -> ClosestPair.bruteForce(p[0], p[1]).i());
                    // limiar 3 = recursao ate o fim, sem atalho para forca bruta
                    record(out, 2, "divisao_conquista_pura", n, run, () -> ClosestPair.divideAndConquer(p[0], p[1], 3).i());
                }
            }
            System.out.println("cruzamento ok");
        }
    }

    // ------------------------------------------------------------------ utilitarios

    static void record(PrintWriter out, int stage, String algo, int n, int run, LongSupplier f) {
        long[] m = measure(f);
        out.printf(Locale.ROOT, "%d,%s,%d,%d,%d,%d%n", stage, algo, n, run, m[0], m[1]);
    }

    /** Retorna {repeticoes, tempo medio por chamada em ns}. */
    static long[] measure(LongSupplier f) {
        long t0 = System.nanoTime();
        sink += f.getAsLong();
        long single = System.nanoTime() - t0;
        if (single >= MIN_BATCH_NS) return new long[] {1, single};
        int reps = (int) Math.min(1_000_000, Math.max(1, MIN_BATCH_NS / Math.max(single, 1)));
        t0 = System.nanoTime();
        for (int r = 0; r < reps; r++) sink += f.getAsLong();
        long total = System.nanoTime() - t0;
        return new long[] {reps, total / reps};
    }

    /** Aquecimento do JIT para que as primeiras medicoes nao incluam compilacao. */
    static void warmUp(Random rnd) {
        for (int k = 0; k < 300; k++) {
            int[] a = randomArray(rnd, 2000);
            sink += MaxSubarray.bruteForce(a).sum() + MaxSubarray.divideAndConquer(a).sum() + MaxSubarray.kadane(a).sum();
            double[][] p = randomPoints(rnd, 2000);
            sink += ClosestPair.bruteForce(p[0], p[1]).i() + ClosestPair.divideAndConquer(p[0], p[1]).i()
                    + ClosestPair.divideAndConquer(p[0], p[1], 3).i();
        }
    }

    static int[] sizes(int from, int to) {
        int count = 0;
        for (int n = from; n <= to; n *= 2) count++;
        int[] s = new int[count];
        for (int k = 0, n = from; k < count; k++, n *= 2) s[k] = n;
        return s;
    }

    /** Variacoes diarias entre -1000 e 1000. */
    static int[] randomArray(Random rnd, int n) {
        int[] a = new int[n];
        for (int k = 0; k < n; k++) a[k] = rnd.nextInt(2001) - 1000;
        return a;
    }

    /** Pontos uniformes no quadrado [0, 10^6) x [0, 10^6). */
    static double[][] randomPoints(Random rnd, int n) {
        double[] xs = new double[n], ys = new double[n];
        for (int k = 0; k < n; k++) {
            xs[k] = rnd.nextDouble() * 1e6;
            ys[k] = rnd.nextDouble() * 1e6;
        }
        return new double[][] {xs, ys};
    }

    static String machineInfo() {
        Runtime rt = Runtime.getRuntime();
        return String.format(Locale.ROOT, "Java %s (%s) | %s %s | %d nucleos logicos | heap max %d MB",
                System.getProperty("java.version"), System.getProperty("java.vm.name"),
                System.getProperty("os.name"), System.getProperty("os.arch"),
                rt.availableProcessors(), rt.maxMemory() / (1024 * 1024));
    }
}
