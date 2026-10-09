// Nomes de texto (strings.xml) repetidos entre módulos com valores DIFERENTES.
// No APK os nomes são globais: um módulo pode acabar mostrando o texto do outro
// (aconteceu com "nenhum_resultado" na pesquisa, 2026-10-09).
// Uso: node ferramentas/textos-repetidos.mjs   (sai com 1 se achar algum)
import fs from 'node:fs'
import path from 'node:path'

const ignorar = new Set(['build', 'node_modules', '.git', '.gradle', 'app-legado'])
const arquivos = []
function andar(pasta) {
  for (const item of fs.readdirSync(pasta, { withFileTypes: true })) {
    const caminho = path.join(pasta, item.name)
    if (item.isDirectory()) {
      if (!ignorar.has(item.name)) andar(caminho)
    } else if (caminho.endsWith(path.join('values', 'strings.xml'))) {
      arquivos.push(caminho)
    }
  }
}
andar('.')

const porNome = new Map()
for (const arquivo of arquivos) {
  const modulo = arquivo.split(path.sep).slice(0, 2).join('/')
  const xml = fs.readFileSync(arquivo, 'utf8')
  for (const m of xml.matchAll(/<(string|plurals) name="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/g)) {
    const valor = m[3].trim().replace(/\s+/g, ' ')
    if (!porNome.has(m[2])) porNome.set(m[2], [])
    porNome.get(m[2]).push({ modulo, valor })
  }
}

let problemas = 0
for (const [nome, usos] of porNome) {
  if (new Set(usos.map((u) => u.valor)).size > 1) {
    problemas++
    console.log(`${nome}: ${usos.map((u) => `${u.modulo} = "${u.valor}"`).join(' | ')}`)
  }
}
if (problemas) {
  console.log(`\n${problemas} nome(s) com textos diferentes entre módulos. Dê nomes únicos.`)
  process.exit(1)
}
console.log('Nenhum nome de texto repetido com valores diferentes.')
