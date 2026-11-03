# Trabalho Prático: Divisão e Conquista

Dupla: **Matheus Cruz** e **Enzo Fernandes**

Implementação em **Java 17+** (testado com OpenJDK 25) de:

- **Etapa 1 – Subarranjo de soma máxima:** força bruta O(n²), divisão e conquista O(n log n) e,
  como questão desafio, o algoritmo de Kadane O(n) (`src/MaxSubarray.java`).
- **Etapa 2 – Par de pontos mais próximos:** força bruta O(n²) e divisão e conquista O(n log n)
  com limiar para força bruta em subproblemas pequenos (`src/ClosestPair.java`).
- **Etapa 3 – Análise experimental:** medições (`src/Benchmark.java`), CSVs brutos em
  `resultados/`, gráficos e tabelas gerados por `scripts/analise.js` e relatório em
  `relatorio/relatorio.pdf`.

## Estrutura

```
src/MaxSubarray.java     Etapa 1 (forca bruta, divisao e conquista, Kadane)
src/ClosestPair.java     Etapa 2 (forca bruta, divisao e conquista com limiar)
src/Main.java            programa de linha de comando (le stdin, escreve stdout)
src/TestRunner.java      bateria de testes
src/Benchmark.java       medicoes de tempo -> resultados/*.csv
tests/casos_etapa1.txt   casos de teste da Etapa 1 (entrada, saida esperada, justificativa)
tests/casos_etapa2.txt   casos de teste da Etapa 2
resultados/              CSVs com as medicoes brutas e logs com a configuracao da maquina
scripts/analise.js       gera graficos (SVG) e tabelas a partir dos CSVs
relatorio/               relatorio (HTML fonte e PDF)
```

## Como compilar e executar

Requisito: JDK 17 ou superior (`javac` e `java` no PATH).

```sh
javac -encoding UTF-8 -d out src/*.java
```

### Etapa 1

Entrada: `n` seguido de `n` inteiros. Saída: `soma inicio fim`.

```sh
echo "9  -2 1 -3 4 -1 2 1 -5 4" | java -cp out Main etapa1          # divisao e conquista
echo "9  -2 1 -3 4 -1 2 1 -5 4" | java -cp out Main etapa1 bf       # forca bruta
echo "9  -2 1 -3 4 -1 2 1 -5 4" | java -cp out Main etapa1 kadane   # Kadane O(n)
# saida: 6 3 6
```

Convenções:
- índices começam em **0** e o intervalo `[inicio, fim]` é fechado;
- o subarranjo nunca é vazio (se todos forem negativos, a resposta é o maior elemento);
- **empate** de soma: vence o menor `inicio`; persistindo o empate, o menor `fim`
  (é o primeiro intervalo encontrado pela força bruta). As três implementações seguem a mesma regra.

### Etapa 2

Entrada: `n` seguido de `n` pares `x y` (números reais). Saída: `distancia i j`, com a distância
em 6 casas decimais e `i < j` os índices (base 0) dos dois pontos na entrada.

```sh
printf "4\n0 0\n10 10\n3 4\n0 1\n" | java -cp out Main etapa2       # divisao e conquista
printf "4\n0 0\n10 10\n3 4\n0 1\n" | java -cp out Main etapa2 bf    # forca bruta
# saida: 1.000000 0 3
```

Se vários pares empatarem na menor distância, qualquer um deles é uma resposta válida.

## Testes

```sh
java -cp out TestRunner
```

Executa os casos de `tests/casos_etapa1.txt` e `tests/casos_etapa2.txt` em todas as
implementações (na Etapa 2, a divisão e conquista roda com limiares 3, 4, 8 e 32 para que até os
casos pequenos passem pela recursão e pela faixa) e depois 5.000 entradas aleatórias comparando
cada algoritmo com a força bruta. Termina com código 0 se tudo passar.

Formato dos arquivos de casos:

```
=== nome_do_caso | justificativa
<entrada>
--- esperado
<saida esperada>
```

## Medições

```sh
java -Xmx6g -cp out Benchmark limiar       # resultados/limiar.csv
java -Xmx6g -cp out Benchmark cruzamento   # resultados/cruzamento.csv
java -Xmx6g -cp out Benchmark tempos       # resultados/tempos.csv
node scripts/analise.js                    # relatorio/gerado/*.svg, *.html, resumo.json
```

Cada tamanho é medido em 10 execuções (entrada aleatória nova a cada execução, semente fixa).
Chamadas muito rápidas são repetidas até somar 2 ms e o CSV guarda o tempo médio por chamada
(coluna `repeticoes`). Antes de medir, o JIT é aquecido.

Máquina usada nas medições: Intel Core i7-12700H (14 núcleos / 20 threads), 16 GB de RAM,
Windows 11 Pro 64 bits, OpenJDK 25.0.4 (HotSpot 64-Bit Server VM), `-Xmx6g`.
