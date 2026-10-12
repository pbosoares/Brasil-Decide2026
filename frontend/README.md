# Brasil Decide 2026 — dashboard

React 19, Vite 7, TypeScript, Tailwind CSS, Lucide e Recharts. Exclusivamente Presidente, segundo turno de 25/10/2026. Sem dados de demonstração no build.

## Executar localmente

Requisitos: Node.js 22.12+ ou 24 e npm. O CI usa Node.js 22. No diretório `frontend`:

```sh
npm ci
npm run dev
```

Abra http://127.0.0.1:5173. Com `VITE_RESULTS_MANIFEST_URL` vazio, o servidor de desenvolvimento do Vite encaminha `/results/*` para `/api/v1/results/*` no backend local em `http://127.0.0.1:8080`. O backend não precisa estar ativo para visualizar a interface, mas, sem ele, a consulta falha e o dashboard exibe o aviso de atualização indisponível. Se houver um snapshot válido salvo no navegador, ele é preservado e sinalizado como desatualizado.

Para visualizar o estado de espera estático sem backend, execute `npm run build` e `npm run preview`, com `VITE_RESULTS_MANIFEST_URL` vazio durante o build, e abra http://127.0.0.1:4173. Essa prévia serve `public/results/manifest.json`, sem votos. Os cartões continuam mostrando as candidaturas e fotos cadastradas localmente. O navegador não consulta o TSE para buscar resultados.

```sh
npm test
npm run build
npm run preview
npx playwright install chromium
npm run test:e2e
```

No Windows com Chrome instalado, pode usar `$env:PLAYWRIGHT_CHANNEL='chrome'` antes de `npm run test:e2e` para evitar baixar outro navegador. O CI usa Chromium. Build em `dist/`, adequado para frontend estático em Cloudflare Pages ou para a imagem Nginx usada na VPS. O [CI/CD](../.github/workflows/ci.yml) valida e publica as imagens a partir de `main`, quando `AUTO_DEPLOY=true`.

## Comportamento

- Placar com candidaturas, partido, número, votos, percentuais oficiais, barras e diferença. `src/candidates.ts` mantém os perfis de Lula e Flavio Bolsonaro, exibidos também sem snapshot, com fotos locais em `public/candidates/`. Quando há resultados, as candidaturas vêm do snapshot; para IDs conhecidos, o nome e a foto locais têm prioridade. Para outros IDs, `fotoUrl` opcional do snapshot é validado para a origem HTTPS oficial do TSE; sem foto, usa ícone neutro. O cadastro local contém identificação e retratos, sem votos ou percentuais.
- Estatísticas nacionais, votos brancos e nulos discriminados, seções e percentual.
- Mapa SVG real das 27 UFs, seleção por clique/teclado, tooltip, legenda por candidatura, cor neutra para empate/sem dados e contagem de lideranças. Cores dependem de número/ID estáveis, não de posição na apuração.
- Painel estadual, seletor acessível e histórico somente de atualizações efetivamente recebidas nesta sessão. Sem curva inventada; o gráfico é carregado sob demanda após duas gerações distintas.
- Tabela com busca sem distinção de acentos, ordenação por todas as colunas e seleção de estado. Atalho `/` foca a busca.
- Polling sequencial com intervalo de 5–6 segundos na página visível, 30–31 segundos em segundo plano e tentativas progressivas após falha, timeout, checagem de versões e validação Zod. Uma falha ou documento inválido preserva o último snapshot válido em memória e, quando permitido, em localStorage. Dados antigos são sinalizados mesmo quando o arquivo em cache responde.
- Aviso independente, fonte TSE, horário de Brasília, distinção entre liderança e totalização final. Nenhuma eleição é declarada com base nos cálculos de liderança.
- Contador opcional de aberturas da página via `POST /api/v1/visits`, incluindo recarregamentos. O Nginx do frontend e o servidor de desenvolvimento do Vite encaminham esse endpoint ao backend. Para usar o contador em desenvolvimento, inicie o backend com `VISITS_ORIGIN=http://127.0.0.1:5173`, pois o endpoint valida a origem recebida. Hospedagem estática e `npm run preview` precisam de uma rota equivalente para exibir a contagem. Se o serviço estiver indisponível, mostra `—` e mantém o dashboard utilizável.

## Dados públicos

`VITE_RESULTS_MANIFEST_URL` define a URL pública do manifest na CDN e é incorporada durante o build. Vazio usa `/results/manifest.json`: em `npm run dev`, a requisição passa pelo proxy para o backend local; no build estático e em `npm run preview`, usa o manifest de espera incluído no projeto. Consulte [o contrato](../docs/snapshots-publicos.md). Para distribuição pública dos resultados, configure a URL da CDN, sem apontar para `/api/v1` da VPS ou para o TSE. O publicador R2 está implementado no backend; a configuração externa do bucket, credenciais, domínio e cache/CORS permanece pendente, conforme [publicação](../docs/publicacao.md). A coleta é desativada por padrão.

## Estilos

`src/styles.css` concentra os estilos do dashboard, incluindo tema, componentes, media queries e movimento reduzido. A ordem das regras preserva a cascata do layout existente; o antigo `refinement.css` foi incorporado após remover declarações sobrescritas. A consolidação isolada foi comparada em 12 larguras, de 320 a 1440 px, sem diferenças de dimensões ou estilos calculados. Uma correção adicional permite que votos e percentuais dos cartões ocupem linhas diferentes quando necessário, limita as colunas das métricas e ajusta a fonte em celulares para comportar contagens grandes.

## Mapa

Fonte: [API de malhas do IBGE](https://servicodados.ibge.gov.br/api/docs/malhas?versao=3). Arquivo oficial simplificado baixado em 10/10/2026:

`https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR?formato=application/vnd.geo%2Bjson&qualidade=minima&intrarregiao=UF`

O original está em `scripts/source/brasil-uf.geojson`; SHA-256 `07c671aa87aa1ab296c446de6294df9827112782537b996c0a160284907e7c0f`. `python scripts/build-map.py` a partir de `frontend` regenera `src/assets/brazil.json` com projeção simples, precisão reduzida e atribuição. O navegador não consulta o IBGE. O mapa é informativo, não destinado a medições cartográficas.

## Testes

Os exemplos em `tests/fixtures.ts` são identificados como **SIMULAÇÃO exclusiva de testes** e não são importados pela aplicação. Testes de navegador interceptam URLs em contexto isolado; nenhum arquivo com votos simulados é servido pela prévia ou incluído em `dist`.

Testes cobrem turno/fase, totais e liderança, snapshot incompleto, fotos de origem incorreta, idade da fonte, espera sem votos, mapa/teclado, busca/ordenação, histórico, preservação após falha, fallback de fotos e contador com falha, timeout e ausência de `AbortSignal.timeout`. A responsividade é verificada em 320, 390, 520, 600, 760, 768, 800, 900, 1000, 1100, 1280 e 1440 px, tanto em espera quanto com votações grandes simuladas exclusivamente nos testes.
