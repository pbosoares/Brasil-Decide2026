# API interna v1

Todos os recursos são somente leitura; UTF-8 e JSON. Respostas de sucesso usam `Cache-Control: no-store`, porque a publicação na CDN tem política independente. Datas são ISO 8601; instantes UTC; a interface deve exibir America/Sao_Paulo. Votos são inteiros de 64 bits; percentuais são decimais. O frontend deverá validar inteiros seguros ao consumir JSON.

| GET | Corpo 200 | Sem dados |
| --- | --- | --- |
| `/api/v1/elections/current` | Eleicao | 503 |
| `/api/v1/results/national` | `{ "resultado": Resultado }` | 503 |
| `/api/v1/results/states` | lista de `{ "uf": string, "resultado": Resultado }`, ordenada por UF | 503 |
| `/api/v1/results/states/{uf}` | `{ "uf": string, "resultado": Resultado }` | 503; UF inválida retorna 404 |
| `/api/v1/results/status` | StatusDaFonte | sempre disponível |
| `/actuator/health` | `{ "status": "UP" }` | status operacional, não frescor dos resultados |

UF aceita maiúscula ou minúscula; exterior não é uma das 27 UFs da API.

## Eleicao

`codigo`, `pleito`, `ciclo`, `nome`: strings obtidas do EA11. `turno`: `{numero: 2, data: "2026-10-25"}`. `simulacao`: boolean (false no coletor oficial). Não há um objeto fictício antes da descoberta da fonte.

## Resultado

`uf`: BR ou sigla estadual; `geracao`: identificador do TSE, string opaca, não presumir ordenação numérica; `atualizadoEm`: instante de geração do arquivo; `divulgacaoPermitida`: indicador oficial; `simulacao`: boolean; `candidatos`: lista ordenada por votos descrescente (ID desempata apenas a ordenação); `votos`: Votos; `totalizacao`: Totalizacao; `liderId`: ID do primeiro candidato, null para empate, zero votos ou divulgação não autorizada; `diferencaVotos`: diferença entre primeiro e segundo.

**Candidato**: `id` (sqcand), `numero`, `nome` (nome de urna), `partido`, `votos` (vap), `percentualOficial` (pvapn), `destinacao` (dvt), `situacao` (st), `indicadoEleitoPeloTse` (e=s). Interpretar o indicador junto com a situação e totalização oficiais, nunca como consequência da liderança calculada. Não há fotos inventadas; resolução de fotos pelo EA11 é trabalho futuro.

**Totalizacao**: `secoes`, `secoesTotalizadas`, `percentual` (pstn), `andamento` (n/p/f), `totalizacaoFinal` (tf=s). `and=f` estadual não declara eleição presidencial concluída; a condição nacional e os indicadores de candidato são decisivos.

**Votos**: `total` (tv), `validos` (vv), `brancos` (vb), `nulos` (tvn, inclui técnicos), `nulosTecnicos` (vnt, subconjunto de nulos), `anulados` (van), `anuladosSubJudice` (vansj), `semCandidato` (vscv). Não somar subconjuntos ao total. Percentuais dos candidatos são os fornecidos pelo TSE; não recalcular usando tv.

## StatusDaFonte

`estado`: AGUARDANDO_FONTE, RECUPERADO_DO_DISCO, DISPONIVEL ou INDISPONIVEL. `ultimaTentativa`: início do último ciclo; `ultimaVerificacao`: ciclo completo válido, inclusive respostas 304; `ultimaAtualizacao`: geração nacional; `desatualizado`: true se não há snapshot, fonte indisponível, verificação expirada ou resultados ainda parciais sem geração recente; `versao`: UUID do snapshot ou null; `fonte`: TSE; `aviso`: declaração de independência. Datas podem ser null quando nunca verificadas. Um resultado final sem nova geração não fica atrasado apenas por sua idade; falhas de verificação continuam sinalizadas.

Erros não expõem detalhes internos ou URLs sensíveis. O consumidor deve usar o status da fonte ao mostrar resultados preservados. Os endpoints separados são internos; o navegador consumirá um único snapshot versionado para evitar misturar leituras de versões distintas.
