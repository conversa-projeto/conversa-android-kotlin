// Proxy HTTP (sem TLS) para testar o app no EMULADOR contra o servidor de desenvolvimento,
// sem instalar a CA do mkcert no aparelho. Faz o mesmo que o nginx do servidor:
//   /storage/...  → MinIO (127.0.0.1:9000), tirando o /storage e mantendo o Host
//                   (o MinIO valida a assinatura da URL com ele);
//   o resto       → API (127.0.0.1:8080), com X-Forwarded-Proto: http, inclusive o WebSocket /ws/.
// O servidor monta as URLs de anexo com o Host + X-Forwarded-Proto da requisição
// (conversa/src/contexto.ts), então elas voltam apontando para este proxy.
//
// Uso:  node ferramentas/proxy-dev.mjs            (porta 8081)
//       adb reverse tcp:8081 tcp:8081
//       no app (build de debug): servidor = http://localhost:8081
// Só para desenvolvimento: escuta apenas em 127.0.0.1. Guia: docs/desenvolvimento/emulador.md.
//
// Simular URL vencida: URL_VENCIDA=1 node ferramentas/proxy-dev.mjs
//   o primeiro GET de cada arquivo em /storage recebe 403 (como o MinIO com a URL expirada);
//   os seguintes passam. Serve para testar a renovação da URL no app (TODO 4.3).

import http from 'node:http'
import net from 'node:net'

const PORTA = Number(process.env.PORTA ?? 8081)
const API = { host: '127.0.0.1', port: Number(process.env.API_PORTA ?? 8080) }
const MINIO = { host: '127.0.0.1', port: Number(process.env.MINIO_PORTA ?? 9000) }
const URL_VENCIDA = process.env.URL_VENCIDA === '1'
const jaRecusados = new Set()

function destino(url) {
  if (url.startsWith('/storage/')) return { ...MINIO, path: url.slice('/storage'.length) }
  return { ...API, path: url }
}

const servidor = http.createServer((req, res) => {
  if (URL_VENCIDA && req.method === 'GET' && req.url.startsWith('/storage/')) {
    const arquivo = req.url.split('?')[0]
    if (!jaRecusados.has(arquivo)) {
      jaRecusados.add(arquivo)
      console.log('URL_VENCIDA: 403 na primeira leitura de', arquivo.slice(-20))
      res.writeHead(403, { 'content-type': 'application/xml' })
      res.end('<Error><Code>AccessDenied</Code><Message>Request has expired</Message></Error>')
      return
    }
  }
  const alvo = destino(req.url)
  const cabecalhos = { ...req.headers, 'x-forwarded-proto': 'http' }
  const repasse = http.request({ ...alvo, method: req.method, headers: cabecalhos }, (resposta) => {
    res.writeHead(resposta.statusCode ?? 502, resposta.headers)
    resposta.pipe(res)
  })
  repasse.on('error', (erro) => {
    if (!res.headersSent) res.writeHead(502, { 'content-type': 'text/plain' })
    res.end(`proxy-dev: ${erro.message}`)
  })
  req.pipe(repasse)
})

// WebSocket: repassa o pedido de upgrade cru para a API e liga os dois sockets.
servidor.on('upgrade', (req, socket, cabeca) => {
  const alvo = destino(req.url)
  const conexao = net.connect(alvo.port, alvo.host, () => {
    const linhas = [`${req.method} ${alvo.path} HTTP/1.1`]
    for (const [nome, valor] of Object.entries({ ...req.headers, 'x-forwarded-proto': 'http' })) {
      for (const v of Array.isArray(valor) ? valor : [valor]) linhas.push(`${nome}: ${v}`)
    }
    conexao.write(linhas.join('\r\n') + '\r\n\r\n')
    if (cabeca?.length) conexao.write(cabeca)
    conexao.pipe(socket)
    socket.pipe(conexao)
  })
  conexao.on('error', () => socket.destroy())
  socket.on('error', () => conexao.destroy())
})

servidor.listen(PORTA, '127.0.0.1', () => {
  console.log(`proxy-dev em http://127.0.0.1:${PORTA} → API ${API.host}:${API.port}, /storage → MinIO ${MINIO.host}:${MINIO.port}`)
})
