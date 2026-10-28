import java.util.Arrays;

/**
 * Etapa 2 - Par de Pontos Mais Proximos.
 *
 * A resposta e a menor distancia euclidiana e os indices (na entrada, base 0) dos dois pontos.
 * Se houver mais de um par com a mesma distancia minima, qualquer um deles e valido.
 */
public final class ClosestPair {

    /** Limiar padrao: subproblemas com ate LIMIAR pontos sao resolvidos por forca bruta. */
    public static final int DEFAULT_THRESHOLD = 32; // escolhido experimentalmente (resultados/limiar.csv)

    public record Result(double distance, int i, int j) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%.6f %d %d", distance, Math.min(i, j), Math.max(i, j));
        }
    }

    private ClosestPair() {}

    /** Forca bruta O(n^2): compara todos os n(n-1)/2 pares. */
    public static Result bruteForce(double[] xs, double[] ys) {
        requireAtLeastTwo(xs, ys);
        double best = Double.POSITIVE_INFINITY;
        int bi = 0, bj = 1;
        for (int i = 0; i < xs.length; i++) {
            for (int j = i + 1; j < xs.length; j++) {
                double dx = xs[i] - xs[j], dy = ys[i] - ys[j];
                double d2 = dx * dx + dy * dy;
                if (d2 < best) {
                    best = d2;
                    bi = i;
                    bj = j;
                }
            }
        }
        return new Result(Math.sqrt(best), bi, bj);
    }

    public static Result divideAndConquer(double[] xs, double[] ys) {
        return divideAndConquer(xs, ys, DEFAULT_THRESHOLD);
    }

    /**
     * Divisao e conquista O(n log n): T(n) = 2T(n/2) + Theta(n).
     * As duas ordenacoes iniciais (por x e por y) usam a biblioteca padrao; dentro da
     * recursao a lista por y e apenas particionada, sem reordenar.
     *
     * @param threshold tamanho a partir do qual (inclusive para baixo) se usa forca bruta; minimo 3
     */
    public static Result divideAndConquer(double[] xs, double[] ys, int threshold) {
        return solveSorted(presort(xs, ys), threshold);
    }

    /** Pontos ja ordenados por x e por y (resultado da etapa de pre-processamento). */
    public record Sorted(double[] xs, double[] ys, int[] px, int[] rank, int[] py) {}

    /** Pre-processamento O(n log n): as duas ordenacoes com a biblioteca padrao. */
    public static Sorted presort(double[] xs, double[] ys) {
        requireAtLeastTwo(xs, ys);
        int n = xs.length;

        // px: indices ordenados por (x, indice). rank[p] = posicao de p em px; serve para
        // dividir corretamente mesmo quando varios pontos tem o mesmo x da mediana.
        int[] px = sortedIndices(xs);
        int[] rank = new int[n];
        for (int k = 0; k < n; k++) rank[px[k]] = k;
        int[] py = sortedIndices(ys);
        return new Sorted(xs, ys, px, rank, py);
    }

    /**
     * Indices 0..n-1 ordenados por (v[indice], indice), usando so ordenacoes de tipos primitivos
     * da biblioteca padrao (ordenar Integer[] com Comparator e bem mais lento e gera muito lixo):
     *  1. ordena uma copia de v e troca cada valor pela sua posicao (rank) entre os valores distintos;
     *  2. ordena chaves long = rank * 2^32 + indice.
     */
    static int[] sortedIndices(double[] v) {
        int n = v.length;
        double[] sorted = v.clone();
        Arrays.sort(sorted);
        long[] keys = new long[n];
        for (int k = 0; k < n; k++) keys[k] = ((long) lowerBound(sorted, v[k]) << 32) | k;
        Arrays.sort(keys);
        int[] idx = new int[n];
        for (int k = 0; k < n; k++) idx[k] = (int) keys[k]; // 32 bits baixos = indice
        return idx;
    }

    /** Primeira posicao de sorted com valor >= x (mesma ordem de Arrays.sort / Double.compare). */
    private static int lowerBound(double[] sorted, double x) {
        int lo = 0, hi = sorted.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (Double.compare(sorted[mid], x) < 0) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Fase recursiva (dividir, conquistar, combinar) sobre pontos ja ordenados. Nao altera ps. */
    public static Result solveSorted(Sorted ps, int threshold) {
        Solver s = new Solver(ps.xs(), ps.ys(), ps.px(), ps.rank(), Math.max(3, threshold));
        s.solve(0, ps.px().length, ps.py());
        return new Result(Math.sqrt(s.bestD2), s.bi, s.bj);
    }

    /** Estado da recursao: guarda o melhor par encontrado ate agora (distancias ao quadrado). */
    private static final class Solver {
        final double[] xs, ys;
        final int[] px, rank;
        final int threshold;
        final int[] buffer; // area auxiliar para a particao por y e para a faixa
        double bestD2 = Double.POSITIVE_INFINITY;
        int bi = -1, bj = -1;

        Solver(double[] xs, double[] ys, int[] px, int[] rank, int threshold) {
            this.xs = xs;
            this.ys = ys;
            this.px = px;
            this.rank = rank;
            this.threshold = threshold;
            this.buffer = new int[px.length];
        }

        void consider(int p, int q) {
            double dx = xs[p] - xs[q], dy = ys[p] - ys[q];
            double d2 = dx * dx + dy * dy;
            if (d2 < bestD2) {
                bestD2 = d2;
                bi = p;
                bj = q;
            }
        }

        /**
         * Resolve px[lo..hi). py contem exatamente esses pontos ordenados por y.
         * Ao final, bestD2 <= menor distancia^2 dentro do intervalo.
         */
        void solve(int lo, int hi, int[] py) {
            int n = hi - lo;
            if (n <= threshold) {
                for (int a = lo; a < hi; a++)
                    for (int b = a + 1; b < hi; b++)
                        consider(px[a], px[b]);
                return;
            }

            int mid = lo + n / 2;
            double midX = xs[px[mid]]; // linha vertical na mediana das coordenadas x

            // Particiona py em esquerda (rank < mid) e direita (rank >= mid), mantendo a ordem por y.
            int[] pyl = new int[mid - lo];
            int[] pyr = new int[hi - mid];
            int l = 0, r = 0;
            for (int p : py) {
                if (rank[p] < mid) pyl[l++] = p;
                else pyr[r++] = p;
            }

            solve(lo, mid, pyl);
            solve(mid, hi, pyr);

            // delta = menor distancia encontrada nos dois lados (pode ser ainda menor, o que so ajuda).
            double delta = Math.sqrt(bestD2);

            // Faixa de largura 2*delta em torno da linha, ja ordenada por y.
            int m = 0;
            for (int p : py) {
                if (Math.abs(xs[p] - midX) < delta) buffer[m++] = p;
            }

            // Cada ponto so precisa ser comparado com os ate 7 seguintes na faixa (ver relatorio).
            for (int a = 0; a < m; a++) {
                int p = buffer[a];
                for (int b = a + 1; b < m && b <= a + 7; b++) {
                    int q = buffer[b];
                    if (ys[q] - ys[p] >= delta) break;
                    consider(p, q);
                    delta = Math.min(delta, Math.sqrt(bestD2));
                }
            }
        }
    }

    private static void requireAtLeastTwo(double[] xs, double[] ys) {
        if (xs == null || ys == null || xs.length != ys.length || xs.length < 2) {
            throw new IllegalArgumentException("sao necessarios pelo menos 2 pontos");
        }
    }
}
