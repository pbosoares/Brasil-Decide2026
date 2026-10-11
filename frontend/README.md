# Brasil Decide 2026 — dashboard

React 19, Vite 7, TypeScript, Tailwind CSS, Lucide e Recharts. Exclusivamente Presidente, segundo turno de 25/10/2026. Sem dados de demonstração no build.

## Executar localmente

Requisitos: Node.js 24 e npm. No diretório `frontend`:

```sh
npm ci
npm run dev
```

Abra http://127.0.0.1:5173. O backend não precisa estar ativo para visualizar o dashboard. A configuração padrão lê `/results/manifest.json`, que representa apenas o estado de espera, sem votos ou candidaturas inventadas. Não há chamadas do navegador à VPS ou ao TSE para buscar resultados.

```sh
npm test
npm run build
npm run preview
npx playwright install chromium
npm run test:e2e
```

No Windows com Chrome instalado, pode usar `$env:PLAYWRIGHT_CHANNEL='chrome'` antes de `npm run test:e2e` para evitar baixar outro navegador. O CI usa Chromium. Build em `dist/`, adequado para frontend estático em Cloudflare Pages. Nenhuma publicação foi realizada.

## Comportamento

- Placar com candidaturas, partido, número, votos, percentuais oficiais, barras e diferença. `fotoUrl` opcional é aceito apenas da origem HTTPS oficial do TSE; sem foto, usa ícone neutro. O backend atual ainda não enriquece fotos: o publicador precisará descobri-las pelo EA11, quando disponíveis.
- Estatísticas nacionais, votos brancos e nulos discriminados, seções e percentual.
- Mapa SVG real das 27 UFs, seleção por clique/teclado, tooltip, legenda por candidatura, cor neutra para empate/sem dados e contagem de lideranças. Cores dependem de número/ID estáveis, não de posição na apuração.
- Painel estadual, seletor acessível e histórico somente de atualizações efetivamente recebidas nesta sessão. Sem curva inventada; o gráfico é carregado sob demanda após duas gerações distintas.
- Tabela com busca sem distinção de acentos, ordenação por todas as colunas e seleção de estado. Atalho `/` foca a busca.
- Polling sequencial com intervalo de 5–6 segundos na página visível, 30–31 segundos em segundo plano e tentativas progressivas após falha, timeout, checagem de versões e validação Zod. Uma falha ou documento inválido preserva o último snapshot válido em memória e, quando permitido, em localStorage. Dados antigos são sinalizados mesmo quando o arquivo em cache responde.
- Aviso independente, fonte TSE, horário de Brasília, distinção entre liderança e totalização final. Nenhuma eleição é declarada com base nos cálculos de liderança.

## Dados públicos

`VITE_RESULTS_MANIFEST_URL` define a URL pública do manifest na CDN. Vazio usa o arquivo estático de espera. Consulte [o contrato](../docs/snapshots-publicos.md). Não apontar para `/api/v1` da VPS. O publicador R2 e a ativação da coleta ainda são etapas futuras; este dashboard não está conectado a resultados ao vivo.

## Mapa

Fonte: [API de malhas do IBGE](https://servicodados.ibge.gov.br/api/docs/malhas?versao=3). Arquivo oficial simplificado baixado em 10/10/2026:

`https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR?formato=application/vnd.geo%2Bjson&qualidade=minima&intrarregiao=UF`

O original está em `scripts/source/brasil-uf.geojson`; SHA-256 `07c671aa87aa1ab296c446de6294df9827112782537b996c0a160284907e7c0f`. `python scripts/build-map.py` a partir de `frontend` regenera `src/assets/brazil.json` com projeção simples, precisão reduzida e atribuição. O navegador não consulta o IBGE. O mapa é informativo, não destinado a medições cartográficas.

## Testes

Os exemplos em `tests/fixtures.ts` são identificados como **SIMULAÇÃO exclusiva de testes** e não são importados pela aplicação. Testes de navegador interceptam URLs em contexto isolado; nenhum arquivo com votos simulados é servido pela prévia ou incluído em `dist`.

Testes cobrem turno/fase, totais e liderança, snapshot incompleto, fotos de origem incorreta, idade da fonte, espera sem votos, mapa/teclado, busca/ordenação, histórico e preservação após falha. Responsividade verificada em 320, 390, 768 e 1440 px.
