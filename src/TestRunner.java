import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bateria de testes:
 *  1. casos fixos dos arquivos tests/casos_etapa1.txt e tests/casos_etapa2.txt
 *     (todas as implementacoes devem dar a saida esperada);
 *  2. testes aleatorios que comparam divisao e conquista (e Kadane) com a forca bruta.
 *
 * uso: java -cp out TestRunner [pasta_dos_casos]
 */
public final class TestRunner {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    record Case(String name, String why, String input, String expected) {}

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "tests");

        System.out.println("== Etapa 1: casos fixos ==");
        for (Case c : readCases(dir.resolve("casos_etapa1.txt"))) stage1Case(c);
        System.out.println("== Etapa 2: casos fixos ==");
        for (Case c : readCases(dir.resolve("casos_etapa2.txt"))) stage2Case(c);

        System.out.println("== Etapa 1: aleatorios contra forca bruta ==");
        randomStage1();
        System.out.println("== Etapa 2: aleatorios contra forca bruta ==");
        randomStage2();

        System.out.println();
        System.out.printf("%d verificacoes passaram, %d falharam%n", passed, failures.size());
        for (String f : failures) System.out.println("  FALHA: " + f);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ---------------------------------------------------------------- casos fixos

    static List<Case> readCases(Path file) throws IOException {
        List<Case> cases = new ArrayList<>();
        String name = null, why = null;
        StringBuilder input = new StringBuilder(), expected = new StringBuilder();
        boolean inExpected = false;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.startsWith("#")) continue;
            if (line.startsWith("===")) {
                if (name != null) cases.add(new Case(name, why, input.toString(), expected.toString().strip()));
                String[] parts = line.substring(3).split("\\|", 2);
                name = parts[0].strip();
                why = parts.length > 1 ? parts[1].strip() : "";
                input.setLength(0);
                expected.setLength(0);
                inExpected = false;
            } else if (line.startsWith("--- esperado")) {
                inExpected = true;
            } else if (!line.isEmpty() && name != null) {
                (inExpected ? expected : input).append(line).append('\n');
            }
        }
        if (name != null) cases.add(new Case(name, why, input.toString(), expected.toString().strip()));
        return cases;
    }

    static void stage1Case(Case c) {
        String[] tok = c.input().trim().split("\\s+");
        int n = Integer.parseInt(tok[0]);
        int[] a = new int[n];
        for (int k = 0; k < n; k++) a[k] = Integer.parseInt(tok[k + 1]);
        check(c.name() + " [forca bruta]", MaxSubarray.bruteForce(a).toString(), c.expected());
        check(c.name() + " [divisao e conquista]", MaxSubarray.divideAndConquer(a).toString(), c.expected());
        check(c.name() + " [kadane]", MaxSubarray.kadane(a).toString(), c.expected());
        System.out.printf("  %-24s %s%n", c.name(), c.why());
    }

    static void stage2Case(Case c) {
        String[] tok = c.input().trim().split("\\s+");
        int n = Integer.parseInt(tok[0]);
        double[] xs = new double[n], ys = new double[n];
        for (int k = 0; k < n; k++) {
            xs[k] = Double.parseDouble(tok[1 + 2 * k]);
            ys[k] = Double.parseDouble(tok[2 + 2 * k]);
        }
        double expected = Double.parseDouble(c.expected());
        checkPair(c.name() + " [forca bruta]", xs, ys, ClosestPair.bruteForce(xs, ys), expected);
        for (int t : new int[] {3, 4, 8, ClosestPair.DEFAULT_THRESHOLD}) {
            checkPair(c.name() + " [d&c limiar " + t + "]", xs, ys, ClosestPair.divideAndConquer(xs, ys, t), expected);
        }
        System.out.printf("  %-24s %s%n", c.name(), c.why());
    }

    // ---------------------------------------------------------------- aleatorios

    static void randomStage1() {
        Random rnd = new Random(20261028L);
        int trials = 3000;
        for (int t = 0; t < trials; t++) {
            int n = 1 + rnd.nextInt(t < 2000 ? 12 : 300);
            // faixa pequena de valores gera muitos empates e muitos zeros
            int range = switch (t % 3) { case 0 -> 3; case 1 -> 20; default -> 1000; };
            int shift = t % 5 == 0 ? range * 2 : range; // as vezes, quase so negativos
            int[] a = new int[n];
            for (int k = 0; k < n; k++) a[k] = rnd.nextInt(2 * range + 1) - shift;
            String bf = MaxSubarray.bruteForce(a).toString();
            check("aleatorio etapa1 #" + t + " [d&c]", MaxSubarray.divideAndConquer(a).toString(), bf);
            check("aleatorio etapa1 #" + t + " [kadane]", MaxSubarray.kadane(a).toString(), bf);
        }
        System.out.println("  " + trials + " vetores (n de 1 a 300, com muitos empates)");
    }

    static void randomStage2() {
        Random rnd = new Random(42L);
        int trials = 2000;
        for (int t = 0; t < trials; t++) {
            int n = 2 + rnd.nextInt(t < 1500 ? 40 : 600);
            double[] xs = new double[n], ys = new double[n];
            boolean grid = t % 2 == 0; // coordenadas inteiras pequenas: x repetidos, duplicatas, empates
            for (int k = 0; k < n; k++) {
                if (grid) {
                    xs[k] = rnd.nextInt(10);
                    ys[k] = rnd.nextInt(50);
                } else {
                    xs[k] = rnd.nextDouble() * 1000 - 500;
                    ys[k] = rnd.nextDouble() * 1000 - 500;
                }
            }
            double expected = ClosestPair.bruteForce(xs, ys).distance();
            int threshold = new int[] {3, 4, 8, 16, 32}[t % 5];
            checkPair("aleatorio etapa2 #" + t + " [d&c limiar " + threshold + "]", xs, ys,
                    ClosestPair.divideAndConquer(xs, ys, threshold), expected);
        }
        System.out.println("  " + trials + " conjuntos (n de 2 a 600, metade com coordenadas repetidas)");
    }

    // ---------------------------------------------------------------- verificacoes

    static void check(String label, String got, String expected) {
        if (got.equals(expected)) passed++;
        else failures.add(label + ": esperado '" + expected + "', obtido '" + got + "'");
    }

    static void checkPair(String label, double[] xs, double[] ys, ClosestPair.Result r, double expected) {
        double real = Math.hypot(xs[r.i()] - xs[r.j()], ys[r.i()] - ys[r.j()]);
        boolean ok = r.i() != r.j()
                && Math.abs(r.distance() - expected) <= 1e-6
                && Math.abs(real - r.distance()) <= 1e-9 * Math.max(1, real);
        if (ok) passed++;
        else failures.add(label + ": esperado " + expected + ", obtido " + r);
    }
}
