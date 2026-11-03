// Monta relatorio/relatorio.html a partir de relatorio/relatorio_fonte.html, trocando cada
// {{arquivo}} pelo conteudo de relatorio/gerado/arquivo (graficos SVG e tabelas HTML), e gera o
// PDF com o modo headless do Edge ou do Chrome.
//
// uso: node scripts/analise.js && node scripts/relatorio.js
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const DIR = path.join(__dirname, "..", "relatorio");
const src = fs.readFileSync(path.join(DIR, "relatorio_fonte.html"), "utf8");
const html = src.replace(/\{\{([\w.]+)\}\}/g, (_, f) => fs.readFileSync(path.join(DIR, "gerado", f), "utf8"));
const htmlPath = path.join(DIR, "relatorio.html");
fs.writeFileSync(htmlPath, html);

const browsers = [
  "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
  "C:/Program Files/Google/Chrome/Application/chrome.exe",
  "/usr/bin/google-chrome",
  "/usr/bin/chromium",
];
const browser = browsers.find((b) => fs.existsSync(b));
if (!browser) {
  console.log("relatorio.html gerado; nenhum navegador encontrado para gerar o PDF");
  process.exit(0);
}
execFileSync(browser, [
  "--headless", "--disable-gpu", "--no-pdf-header-footer",
  "--print-to-pdf=" + path.join(DIR, "relatorio.pdf"),
  "file:///" + htmlPath.replace(/\\/g, "/"),
], { stdio: "inherit" });
console.log("relatorio.pdf gerado");
