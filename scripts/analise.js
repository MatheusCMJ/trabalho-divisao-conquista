// Le os CSVs de resultados/ e gera, em relatorio/gerado/, os graficos (SVG) e as tabelas (HTML)
// usados no relatorio, alem de um resumo.json com as medias e as inclinacoes log-log.
//
// uso: node scripts/analise.js
const fs = require("fs");
const path = require("path");

const ROOT = path.join(__dirname, "..");
const OUT = path.join(ROOT, "relatorio", "gerado");
fs.mkdirSync(OUT, { recursive: true });

const COLORS = { forca_bruta: "#eb6834", divisao_conquista: "#2a78d6", kadane: "#1baf7a", divisao_conquista_pura: "#2a78d6" };
const NAMES = {
  forca_bruta: "Força bruta",
  divisao_conquista: "Divisão e conquista",
  divisao_conquista_pura: "Divisão e conquista (sem limiar)",
  kadane: "Kadane (O(n))",
};
const DASH = { forca_bruta: "", divisao_conquista: "", kadane: "6 4", divisao_conquista_pura: "" };

function readCsv(file) {
  const [head, ...lines] = fs.readFileSync(path.join(ROOT, "resultados", file), "utf8").trim().split(/\r?\n/);
  const cols = head.split(",");
  return lines.map((l) => Object.fromEntries(l.split(",").map((v, i) => [cols[i], isNaN(+v) ? v : +v])));
}

function stats(values) {
  const mean = values.reduce((a, b) => a + b, 0) / values.length;
  const sd = Math.sqrt(values.reduce((a, b) => a + (b - mean) ** 2, 0) / (values.length - 1 || 1));
  return { mean, sd, runs: values.length };
}

/** {algoritmo: [{n, mean, sd}]} para uma etapa. */
function series(rows, stage) {
  const g = {};
  for (const r of rows.filter((r) => r.etapa === stage)) ((g[r.algoritmo] ??= {})[r.n] ??= []).push(r.tempo_ns);
  const out = {};
  for (const a in g) out[a] = Object.entries(g[a]).map(([n, v]) => ({ n: +n, ...stats(v) })).sort((x, y) => x.n - y.n);
  return out;
}

/** Inclinacao da reta log2(t) x log2(n) por minimos quadrados, so com n >= minN. */
function slope(pts, minN) {
  const p = pts.filter((q) => q.n >= minN);
  const xs = p.map((q) => Math.log2(q.n)), ys = p.map((q) => Math.log2(q.mean));
  const mx = xs.reduce((a, b) => a + b) / xs.length, my = ys.reduce((a, b) => a + b) / ys.length;
  let num = 0, den = 0;
  xs.forEach((x, i) => { num += (x - mx) * (ys[i] - my); den += (x - mx) ** 2; });
  return num / den;
}

// ------------------------------------------------------------------ SVG

function fmtN(n) {
  if (n < 1e4) return n.toLocaleString("pt-BR");
  if (n < 1e6) return String(+(n / 1e3).toFixed(1)).replace(".", ",") + "k";
  return String(+(n / 1e6).toFixed(2)).replace(".", ",") + "M";
}
function fmtT(ns) { return fmtT0(ns).replace(".", ","); }
function fmtT0(ns) {
  if (ns >= 1e9) return +(ns / 1e9).toPrecision(3) + " s";
  if (ns >= 1e6) return +(ns / 1e6).toPrecision(3) + " ms";
  if (ns >= 1e3) return +(ns / 1e3).toPrecision(3) + " µs";
  return Math.round(ns) + " ns";
}

/**
 * Grafico de linhas. opts: {title, xlabel, ylabel, logX, logY, yFmt, xFmt, series:[{key,label,pts:[{x,y}]}], refs:[{label,pts}]}
 */
function lineChart(opts) {
  const W = 640, H = 340, m = { l: 66, r: 16, t: 52, b: 44 };
  const all = opts.series.flatMap((s) => s.pts).concat((opts.refs || []).flatMap((s) => s.pts));
  const tx = opts.logX ? Math.log10 : (v) => v, ty = opts.logY ? Math.log10 : (v) => v;
  let x0 = Math.min(...all.map((p) => tx(p.x))), x1 = Math.max(...all.map((p) => tx(p.x)));
  let y0 = Math.min(...all.map((p) => ty(p.y))), y1 = Math.max(...all.map((p) => ty(p.y)));
  if (opts.logY) { y0 = Math.floor(y0); y1 = Math.ceil(y1); } else { y0 = opts.yMin ?? 0; y1 = y1 * 1.08; }
  if (opts.logX) { x0 -= 0.05; x1 += 0.05; }
  const X = (v) => m.l + ((tx(v) - x0) / (x1 - x0)) * (W - m.l - m.r);
  const Y = (v) => H - m.b - ((ty(v) - y0) / (y1 - y0)) * (H - m.t - m.b);
  const el = [];
  el.push(`<text x="${m.l}" y="18" class="ct">${opts.title}</text>`);
  // grade e eixo y
  const yTicks = [];
  if (opts.logY) for (let e = y0; e <= y1; e++) yTicks.push(10 ** e);
  else { const step = niceStep((y1 - y0) / 5); for (let v = Math.ceil(y0 / step) * step; v <= y1; v += step) yTicks.push(v); }
  for (const v of yTicks) {
    el.push(`<line x1="${m.l}" x2="${W - m.r}" y1="${Y(v)}" y2="${Y(v)}" class="grid"/>`);
    el.push(`<text x="${m.l - 6}" y="${Y(v) + 4}" class="tick" text-anchor="end">${(opts.yFmt || fmtT)(v)}</text>`);
  }
  // eixo x
  const xVals = [...new Set(opts.series.flatMap((s) => s.pts.map((p) => p.x)))].sort((a, b) => a - b);
  const xTicks = opts.xTicks || xVals.filter((_, i) => xVals.length <= 9 || i % 2 === 0);
  for (const v of xTicks) el.push(`<text x="${X(v)}" y="${H - m.b + 16}" class="tick" text-anchor="middle">${(opts.xFmt || fmtN)(v)}</text>`);
  el.push(`<line x1="${m.l}" x2="${W - m.r}" y1="${H - m.b}" y2="${H - m.b}" class="axis"/>`);
  el.push(`<text x="${(m.l + W - m.r) / 2}" y="${H - 6}" class="lab" text-anchor="middle">${opts.xlabel}</text>`);
  el.push(`<text transform="translate(14 ${(m.t + H - m.b) / 2}) rotate(-90)" class="lab" text-anchor="middle">${opts.ylabel}</text>`);
  // referencias (curvas teoricas)
  for (const r of opts.refs || []) {
    el.push(`<polyline points="${r.pts.map((p) => `${X(p.x)},${Y(p.y)}`).join(" ")}" class="ref"/>`);
    const last = r.pts[r.pts.length - 1];
    el.push(`<text x="${X(last.x) - 4}" y="${Y(last.y) - 6}" class="reflab" text-anchor="end">${r.label}</text>`);
  }
  // series
  for (const s of opts.series) {
    const c = COLORS[s.key] || s.color;
    el.push(`<polyline points="${s.pts.map((p) => `${X(p.x)},${Y(p.y)}`).join(" ")}" fill="none" stroke="${c}" stroke-width="2" stroke-dasharray="${DASH[s.key] ?? ""}" stroke-linejoin="round"/>`);
    for (const p of s.pts) el.push(`<circle cx="${X(p.x)}" cy="${Y(p.y)}" r="3.5" fill="${c}" stroke="#fff" stroke-width="1.5"/>`);
  }
  // legenda
  let lx = m.l;
  const ly = 36;
  for (const s of opts.series) {
    const c = COLORS[s.key] || s.color;
    el.push(`<line x1="${lx}" x2="${lx + 18}" y1="${ly}" y2="${ly}" stroke="${c}" stroke-width="2.5" stroke-dasharray="${DASH[s.key] ?? ""}"/>`);
    el.push(`<text x="${lx + 23}" y="${ly + 4}" class="leg">${s.label}</text>`);
    lx += 32 + s.label.length * 6.3;
  }
  return `<svg viewBox="0 0 ${W} ${H}" xmlns="http://www.w3.org/2000/svg" class="chart">${el.join("")}</svg>`;
}

function niceStep(raw) {
  const p = 10 ** Math.floor(Math.log10(raw));
  return [1, 2, 2.5, 5, 10].map((k) => k * p).find((s) => s >= raw);
}

/** Curva teorica c*f(n) ajustada para passar pelo ultimo ponto medido. */
function reference(pts, f, label) {
  const last = pts[pts.length - 1];
  const c = last.mean / f(last.n);
  return { label, pts: pts.map((p) => ({ x: p.n, y: c * f(p.n) })) };
}

// ------------------------------------------------------------------ dados

const tempos = readCsv("tempos.csv");
const e1 = series(tempos, 1), e2 = series(tempos, 2);
const nlogn = (n) => n * Math.log2(n);
const resumo = { etapa1: {}, etapa2: {} };

for (const [stage, data] of [["etapa1", e1], ["etapa2", e2]]) {
  for (const a in data) {
    resumo[stage][a] = {
      inclinacao_loglog: +slope(data[a], 1600).toFixed(3),
      medias: data[a].map((p) => ({ n: p.n, media_ns: Math.round(p.mean), dp_ns: Math.round(p.sd), execucoes: p.runs })),
    };
  }
}

const toPts = (arr) => arr.map((p) => ({ x: p.n, y: p.mean }));

// Figura 1 e 2: tempo x n em escala log-log
fs.writeFileSync(path.join(OUT, "fig_etapa1.svg"), lineChart({
  title: "Etapa 1 — tempo médio por execução (escala log-log)",
  xlabel: "tamanho da entrada n", ylabel: "tempo", logX: true, logY: true,
  series: ["forca_bruta", "divisao_conquista", "kadane"].map((k) => ({ key: k, label: NAMES[k], pts: toPts(e1[k]) })),
  refs: [reference(e1.forca_bruta, (n) => n * n, "c·n²")],
}));
fs.writeFileSync(path.join(OUT, "fig_etapa2.svg"), lineChart({
  title: "Etapa 2 — tempo médio por execução (escala log-log)",
  xlabel: "número de pontos n", ylabel: "tempo", logX: true, logY: true,
  series: ["forca_bruta", "divisao_conquista"].map((k) => ({ key: k, label: NAMES[k], pts: toPts(e2[k]) })),
  refs: [reference(e2.forca_bruta, (n) => n * n, "c·n²"), reference(e2.divisao_conquista, nlogn, "c·n log n")],
}));

// Figura 3: tempo dividido pela complexidade prevista (linha horizontal = previsao confirmada)
function normalized(data, key, f, minN = 0) {
  const pts = data[key].filter((p) => p.n >= minN).map((p) => ({ x: p.n, y: p.mean / f(p.n) }));
  const ref = pts[pts.length - 1].y;
  return pts.map((p) => ({ x: p.x, y: p.y / ref }));
}
const pct = (v) => v.toFixed(2).replace(".", ",");
fs.writeFileSync(path.join(OUT, "fig_normalizado.svg"), lineChart({
  title: "Tempo ÷ complexidade prevista, normalizado pelo maior n (1 = previsão exata)",
  xlabel: "tamanho da entrada n", ylabel: "razão normalizada", logX: true, logY: false, yFmt: pct,
  series: [
    { key: "forca_bruta", label: "E1 força bruta ÷ n²", pts: normalized(e1, "forca_bruta", (n) => n * n) },
    { key: "divisao_conquista", label: "E1 D&C ÷ n log n", pts: normalized(e1, "divisao_conquista", nlogn) },
    { key: "kadane", label: "E1 Kadane ÷ n", pts: normalized(e1, "kadane", (n) => n) },
  ],
}));
fs.writeFileSync(path.join(OUT, "fig_normalizado2.svg"), lineChart({
  title: "Etapa 2 — tempo ÷ complexidade prevista, normalizado pelo maior n",
  xlabel: "número de pontos n", ylabel: "razão normalizada", logX: true, logY: false, yFmt: pct,
  series: [
    { key: "forca_bruta", label: "força bruta ÷ n²", pts: normalized(e2, "forca_bruta", (n) => n * n) },
    { key: "divisao_conquista", label: "D&C ÷ n log n", pts: normalized(e2, "divisao_conquista", nlogn) },
  ],
}));

// Figura 4: limiar
const lim = readCsv("limiar.csv");
const limG = {};
for (const r of lim.filter((r) => r.fase === "recursao")) ((limG[r.n] ??= {})[r.limiar] ??= []).push(r.tempo_ns);
const sortG = {};
for (const r of lim.filter((r) => r.fase === "ordenacao")) (sortG[r.n] ??= []).push(r.tempo_ns);
resumo.limiar = {};
const limSeries = [];
const limColors = ["#2a78d6", "#eb6834", "#1baf7a", "#6250d6"];
Object.keys(limG).forEach((n, i) => {
  const pts = Object.entries(limG[n]).map(([t, v]) => ({ x: +t, y: stats(v).mean })).sort((a, b) => a.x - b.x);
  const best = Math.min(...pts.map((p) => p.y));
  resumo.limiar[n] = {
    ordenacao_ns: Math.round(stats(sortG[n]).mean),
    recursao_ns: Object.fromEntries(pts.map((p) => [p.x, Math.round(p.y)])),
    melhor_limiar: pts.find((p) => p.y === best).x,
  };
  limSeries.push({ key: "n" + n, color: limColors[i], label: "n = " + fmtN(+n), pts: pts.map((p) => ({ x: p.x, y: p.y / best })) });
});
// limiar escolhido: menor media geometrica do tempo relativo (t / melhor de cada n) entre todos os n
const geo = {};
for (const sr of limSeries) for (const p of sr.pts) (geo[p.x] ??= []).push(Math.log(p.y));
resumo.limiar_media_geometrica = Object.fromEntries(Object.entries(geo).map(([t, v]) => [t, +Math.exp(v.reduce((a, b) => a + b) / v.length).toFixed(3)]));
fs.writeFileSync(path.join(OUT, "fig_limiar.svg"), lineChart({
  title: "Etapa 2 — fase recursiva × limiar (1 = melhor limiar para aquele n)",
  xlabel: "limiar (subproblemas com até este nº de pontos usam força bruta)", ylabel: "tempo relativo",
  logX: true, logY: false, yMin: 0.9, yFmt: (v) => v.toFixed(2).replace(".", ","), xFmt: String,
  series: limSeries,
}));

// Figura 5: cruzamento em entradas pequenas
const cz = readCsv("cruzamento.csv");
const c1 = series(cz, 1), c2 = series(cz, 2);
resumo.cruzamento = {
  etapa1: Object.fromEntries(Object.entries(c1).map(([a, v]) => [a, v.map((p) => [p.n, Math.round(p.mean)])])),
  etapa2: Object.fromEntries(Object.entries(c2).map(([a, v]) => [a, v.map((p) => [p.n, Math.round(p.mean)])])),
};
fs.writeFileSync(path.join(OUT, "fig_cruzamento1.svg"), lineChart({
  title: "Etapa 1 — entradas pequenas (log-log)", xlabel: "n", ylabel: "tempo", logX: true, logY: true,
  series: ["forca_bruta", "divisao_conquista"].map((k) => ({ key: k, label: NAMES[k], pts: toPts(c1[k]) })),
}));

// ------------------------------------------------------------------ tabelas

function table(data, keys, ns) {
  let h = "<table><thead><tr><th>n</th>";
  for (const k of keys) h += `<th>${NAMES[k]}<br><span class="sub">média ± desvio</span></th><th class="r">T(n)/T(n/2)</th>`;
  h += "</tr></thead><tbody>";
  for (const n of ns) {
    h += `<tr><td>${n.toLocaleString("pt-BR")}</td>`;
    for (const k of keys) {
      const i = data[k].findIndex((p) => p.n === n);
      if (i < 0) { h += "<td>—</td><td>—</td>"; continue; }
      const p = data[k][i], prev = data[k][i - 1];
      h += `<td>${fmtT(p.mean)} ± ${fmtT(p.sd)}</td><td class="r">${prev && prev.n * 2 === n ? (p.mean / prev.mean).toFixed(2).replace(".", ",") : "—"}</td>`;
    }
    h += "</tr>";
  }
  return h + "</tbody></table>";
}
const BASE = [100, 200, 400, 800, 1600, 3200, 6400];
fs.writeFileSync(path.join(OUT, "tab_etapa1.html"), table(e1, ["forca_bruta", "divisao_conquista", "kadane"], BASE));
fs.writeFileSync(path.join(OUT, "tab_etapa1_grande.html"), table(e1, ["forca_bruta", "divisao_conquista", "kadane"],
  e1.divisao_conquista.map((p) => p.n).filter((n) => n > 6400)));
fs.writeFileSync(path.join(OUT, "tab_etapa2.html"), table(e2, ["forca_bruta", "divisao_conquista"], BASE));
fs.writeFileSync(path.join(OUT, "tab_etapa2_grande.html"), table(e2, ["forca_bruta", "divisao_conquista"],
  e2.divisao_conquista.map((p) => p.n).filter((n) => n > 6400)));

fs.writeFileSync(path.join(OUT, "resumo.json"), JSON.stringify(resumo, null, 1));
console.log("gerado em", OUT);

// Tabela: quanto do tempo da Etapa 2 (D&C) vai para a ordenacao inicial e quanto para a recursao
{
  let h = "<table><thead><tr><th>n</th><th class=\"r\">ordenação inicial</th><th class=\"r\">recursão (limiar 32)</th><th class=\"r\">fração da ordenação</th></tr></thead><tbody>";
  for (const n in resumo.limiar) {
    const s = resumo.limiar[n].ordenacao_ns, r = resumo.limiar[n].recursao_ns[32];
    h += `<tr><td>${(+n).toLocaleString("pt-BR")}</td><td class="r">${fmtT(s)}</td><td class="r">${fmtT(r)}</td><td class="r">${Math.round((100 * s) / (s + r))}%</td></tr>`;
  }
  fs.writeFileSync(path.join(OUT, "tab_fases.html"), h + "</tbody></table>");
}
