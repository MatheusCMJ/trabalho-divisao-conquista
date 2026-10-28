import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;

/**
 * Ponto de entrada por linha de comando. Le a entrada padrao e escreve a resposta.
 *
 *   java -cp out Main etapa1 [dc|bf|kadane]   entrada: n seguido de n inteiros
 *                                             saida:   soma inicio fim   (indices base 0)
 *   java -cp out Main etapa2 [dc|bf]          entrada: n seguido de n pares "x y"
 *                                             saida:   distancia i j      (6 casas, i < j, base 0)
 *
 * O algoritmo padrao e o de divisao e conquista (dc).
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("uso: java -cp out Main etapa1|etapa2 [dc|bf|kadane]");
            System.exit(2);
        }
        String stage = args[0];
        String algo = args.length > 1 ? args[1] : "dc";
        StreamTokenizer in = new StreamTokenizer(new BufferedReader(new InputStreamReader(System.in)));
        in.resetSyntax();
        in.wordChars('!', '~');
        in.whitespaceChars(0, ' ');

        int n = Integer.parseInt(next(in));
        switch (stage) {
            case "etapa1" -> {
                int[] a = new int[n];
                for (int k = 0; k < n; k++) a[k] = Integer.parseInt(next(in));
                MaxSubarray.Result r = switch (algo) {
                    case "bf" -> MaxSubarray.bruteForce(a);
                    case "kadane" -> MaxSubarray.kadane(a);
                    default -> MaxSubarray.divideAndConquer(a);
                };
                System.out.println(r);
            }
            case "etapa2" -> {
                double[] xs = new double[n], ys = new double[n];
                for (int k = 0; k < n; k++) {
                    xs[k] = Double.parseDouble(next(in));
                    ys[k] = Double.parseDouble(next(in));
                }
                ClosestPair.Result r = algo.equals("bf")
                        ? ClosestPair.bruteForce(xs, ys)
                        : ClosestPair.divideAndConquer(xs, ys);
                System.out.println(r);
            }
            default -> {
                System.err.println("etapa desconhecida: " + stage);
                System.exit(2);
            }
        }
    }

    private static String next(StreamTokenizer in) throws IOException {
        if (in.nextToken() == StreamTokenizer.TT_EOF) throw new IOException("entrada terminou antes do esperado");
        return in.sval;
    }
}
