// Proxy HTTP (sem TLS) para testar o app no EMULADOR contra o servidor de desenvolvimento,
// sem instalar a CA do mkcert no aparelho. Faz o mesmo que o nginx do servidor:
//   /storage/...  → Garage (127.0.0.1:3900), tirando o /storage e mantendo o Host
//                   (o Garage valida a assinatura da URL com ele; até o servidor d9b2646
//                   era o MinIO, na 9000);
//   /webrtc/...   → MediaMTX (127.0.0.1:8889), tirando o /webrtc (WHIP/WHEP das chamadas);
//   o resto       → API (127.0.0.1:8080), com X-Forwarded-Proto: http, inclusive o WebSocket /ws/.
// O servidor monta as URLs de anexo com o Host + X-Forwarded-Proto da requisição
// (conversa/src/contexto.ts), então elas voltam apontando para este proxy.
//
// Uso:  node ferramentas/proxy-dev.mjs            (porta 8081)
//       adb reverse tcp:8081 tcp:8081
//       adb reverse tcp:3478 tcp:3478   (chamadas: o TURN do coturn, só TCP, é por onde a mídia passa)
//       no app (build de debug): servidor = http://localhost:8081
// Só para desenvolvimento: escuta apenas em 127.0.0.1. Guia: docs/desenvolvimento/emulador.md.
//
// Simular URL vencida: URL_VENCIDA=1 node ferramentas/proxy-dev.mjs
//   o primeiro GET e o primeiro PUT de cada arquivo em /storage recebem 400, como o Garage
//   com a URL vencida ("Date is too old"; o MinIO respondia 403); os seguintes passam.
//   URL_VENCIDA=403 recusa com 403 (assinatura que não confere). Serve para testar a
//   renovação da URL no app (TODO 4.3 e 4.2).

import http from 'node:http'
import net from 'node:net'

const PORTA = Number(process.env.PORTA ?? 8081)
const API = { host: '127.0.0.1', port: Number(process.env.API_PORTA ?? 8080) }
const ARMAZENAMENTO = { host: '127.0.0.1', port: Number(process.env.GARAGE_PORTA ?? 3900) }
const MEDIAMTX = { host: '127.0.0.1', port: Number(process.env.MEDIAMTX_PORTA ?? 8889) }
const URL_VENCIDA = process.env.URL_VENCIDA ?? ''
const RECUSAS = {
  '1': [400, '<Error><Code>InvalidRequest</Code><Message>Bad request: Date is too old</Message></Error>'],
  '403': [403, '<Error><Code>AccessDenied</Code><Message>Forbidden: Invalid signature</Message></Error>'],
}
const recusa = RECUSAS[URL_VENCIDA]
const jaRecusados = new Set()

function destino(url) {
  if (url.startsWith('/storage/')) return { ...ARMAZENAMENTO, path: url.slice('/storage'.length) }
  if (url.startsWith('/webrtc/')) return { ...MEDIAMTX, path: url.slice('/webrtc'.length) }
  return { ...API, path: url }
}

const servidor = http.createServer((req, res) => {
  if (recusa && (req.method === 'GET' || req.method === 'PUT') && req.url.startsWith('/storage/')) {
    const arquivo = `${req.method} ${req.url.split('?')[0]}`
    if (!jaRecusados.has(arquivo)) {
      jaRecusados.add(arquivo)
      const [codigo, corpo] = recusa
      console.log(`URL_VENCIDA: ${codigo} no primeiro ${req.method} de`, arquivo.slice(-20))
      // O corpo do PUT é lido e descartado, para o cliente receber a resposta inteira.
      req.resume()
      req.on('end', () => {
        res.writeHead(codigo, { 'content-type': 'application/xml' })
        res.end(corpo)
      })
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
  console.log(`proxy-dev em http://127.0.0.1:${PORTA} → API ${API.host}:${API.port}, /storage → Garage ${ARMAZENAMENTO.host}:${ARMAZENAMENTO.port}, /webrtc → MediaMTX ${MEDIAMTX.host}:${MEDIAMTX.port}${recusa ? `, recusando a 1ª URL de cada arquivo com ${recusa[0]}` : ''}`)
})
