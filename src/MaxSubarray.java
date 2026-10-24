/**
 * Etapa 1 - Subarranjo de Soma Maxima.
 *
 * Convencoes (iguais nas tres implementacoes):
 *  - indices comecam em 0 e o intervalo [inicio, fim] e fechado;
 *  - o subarranjo nunca e vazio (com todos negativos, a resposta e o maior elemento);
 *  - empate de soma: vence o menor inicio; persistindo o empate, o menor fim.
 */
public final class MaxSubarray {

    /** Resultado: soma maxima e indices do intervalo. */
    public record Result(long sum, int start, int end) {
        /** true se este resultado e preferivel a o (maior soma, depois menor inicio, depois menor fim). */
        boolean betterThan(Result o) {
            if (sum != o.sum) return sum > o.sum;
            if (start != o.start) return start < o.start;
            return end < o.end;
        }

        @Override
        public String toString() {
            return sum + " " + start + " " + end;
        }
    }

    private MaxSubarray() {}

    /** Forca bruta O(n^2): testa todos os intervalos [i, j] reaproveitando a soma parcial. */
    public static Result bruteForce(int[] a) {
        requireNonEmpty(a);
        long best = Long.MIN_VALUE;
        int bi = 0, bj = 0;
        for (int i = 0; i < a.length; i++) {
            long s = 0;
            for (int j = i; j < a.length; j++) {
                s += a[j];
                // estritamente maior: mantem o primeiro (menor i, depois menor j) em caso de empate
                if (s > best) {
                    best = s;
                    bi = i;
                    bj = j;
                }
            }
        }
        return new Result(best, bi, bj);
    }

    /** Divisao e conquista O(n log n): T(n) = 2T(n/2) + Theta(n). */
    public static Result divideAndConquer(int[] a) {
        requireNonEmpty(a);
        return dc(a, 0, a.length - 1);
    }

    private static Result dc(int[] a, int lo, int hi) {
        if (lo == hi) return new Result(a[lo], lo, lo);
        int mid = lo + (hi - lo) / 2;
        Result left = dc(a, lo, mid);
        Result right = dc(a, mid + 1, hi);
        Result cross = crossing(a, lo, mid, hi);

        Result best = left;
        if (cross.betterThan(best)) best = cross;
        if (right.betterThan(best)) best = right;
        return best;
    }

    /**
     * Melhor subarranjo que cruza o meio, em O(n): melhor sufixo de a[lo..mid] somado ao
     * melhor prefixo de a[mid+1..hi]. As duas escolhas sao independentes, entao escolher o
     * menor inicio (no sufixo) e o menor fim (no prefixo) respeita a regra de desempate.
     */
    private static Result crossing(int[] a, int lo, int mid, int hi) {
        long s = 0, leftBest = Long.MIN_VALUE;
        int bi = mid;
        for (int i = mid; i >= lo; i--) {
            s += a[i];
            if (s >= leftBest) { // >= : em empate prefere o inicio mais a esquerda
                leftBest = s;
                bi = i;
            }
        }
        s = 0;
        long rightBest = Long.MIN_VALUE;
        int bj = mid + 1;
        for (int j = mid + 1; j <= hi; j++) {
            s += a[j];
            if (s > rightBest) { // > : em empate prefere o fim mais a esquerda
                rightBest = s;
                bj = j;
            }
        }
        return new Result(leftBest + rightBest, bi, bj);
    }

    /**
     * Questao desafio - algoritmo de Kadane, O(n).
     * cur e a melhor soma de um subarranjo que termina em j; estende-se o anterior quando
     * cur >= 0 (em empate isso mantem o inicio mais a esquerda).
     */
    public static Result kadane(int[] a) {
        requireNonEmpty(a);
        long cur = a[0], best = a[0];
        int curStart = 0, bi = 0, bj = 0;
        for (int j = 1; j < a.length; j++) {
            if (cur >= 0) {
                cur += a[j];
            } else {
                cur = a[j];
                curStart = j;
            }
            if (cur > best) {
                best = cur;
                bi = curStart;
                bj = j;
            }
        }
        return new Result(best, bi, bj);
    }

    private static void requireNonEmpty(int[] a) {
        if (a == null || a.length == 0) {
            throw new IllegalArgumentException("o vetor deve ter pelo menos 1 elemento");
        }
    }
}
