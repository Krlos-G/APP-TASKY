// Serve o build de producao com /api apontando para o backend local.
//
// Existe porque o service worker - e portanto o Web Push - so roda em build de
// producao: com `ng serve` ele fica desligado (enabled: !isDevMode()). E o
// ensaio local do que a Fatia 8 vai fazer de verdade.
//
// Uso:  npm run build && npm run servir:build

import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer, request } from 'node:http';
import { extname, join, normalize, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const RAIZ = resolve(fileURLToPath(new URL('.', import.meta.url)), '..', 'dist', 'frontend', 'browser');
const PORTA = Number(process.env.PORT ?? 4300);
const BACKEND = { host: 'localhost', port: Number(process.env.BACKEND_PORT ?? 8080) };

const TIPOS = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.ico': 'image/x-icon',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
};

if (!existsSync(join(RAIZ, 'index.html'))) {
  console.error(`Build nao encontrado em ${RAIZ}. Rode "npm run build" antes.`);
  process.exit(1);
}

createServer((req, res) => {
  if (req.url.startsWith('/api/')) {
    encaminhar(req, res);
    return;
  }
  servirArquivo(req, res);
}).listen(PORTA, () => {
  console.log(`Build de producao em http://localhost:${PORTA}`);
  console.log(`/api -> http://${BACKEND.host}:${BACKEND.port}`);
});

function encaminhar(req, res) {
  const upstream = request(
    { ...BACKEND, method: req.method, path: req.url, headers: req.headers },
    (resposta) => {
      res.writeHead(resposta.statusCode, resposta.headers);
      resposta.pipe(res);
    },
  );

  upstream.on('error', (erro) => {
    res.writeHead(502, { 'content-type': 'application/json' });
    res.end(JSON.stringify({ erro: `Backend fora do ar: ${erro.message}` }));
  });

  req.pipe(upstream);
}

function servirArquivo(req, res) {
  const caminho = normalize(decodeURIComponent(new URL(req.url, 'http://x').pathname));
  const alvo = join(RAIZ, caminho);

  // Rota do app (sem extensao) cai no index: o roteamento e do lado do cliente.
  const arquivo =
    alvo.startsWith(RAIZ) && existsSync(alvo) && statSync(alvo).isFile()
      ? alvo
      : join(RAIZ, 'index.html');

  res.writeHead(200, {
    'content-type': TIPOS[extname(arquivo)] ?? 'application/octet-stream',
    // O service worker precisa ser sempre fresco, senao uma versao velha dele
    // fica no comando e nunca entrega a nova.
    'cache-control': arquivo.endsWith('ngsw-worker.js') ? 'no-cache' : 'no-store',
  });
  createReadStream(arquivo).pipe(res);
}
