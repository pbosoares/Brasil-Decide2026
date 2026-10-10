# TSE — referência consultada em 10/10/2026

Fonte: [Informações técnicas de divulgação 2026](https://www.tse.jus.br/eleicoes/informacoes-tecnicas-sobre-a-divulgacao-de-resultados).

- [EA11](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea11-arquivo-de-configuracao-de-eleicoes): eleição, turno, cargo e diretórios parametrizados.
- [EA14](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea14-arquivo-de-acompanhamento-brasil): acesso ao PDF falhou (403/erro). Revisão integral pendente; arquivo real de acompanhamento preservado nos testes.
- [EA15](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea15-arquivo-de-acompanhamento-uf): acompanhamento de abrangências e seções.
- [EA20](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-ea20-arquivo-de-resultado-unificado): estrutura de resultados e candidatos, indicadores e hierarquia dos totais.
- [Instruções de download](https://www.tse.jus.br/eleicoes/eleicoes-2026-content/arquivos/divulgacao-de-resultados/tse-instrucoes-para-download-dos-arquivos-da-divulgacao-2026).

O código 6258 / pleito 3221 foi confirmado no EA11 oficial para 25/10/2026, turno 2, tipo 8, abrangência BR, cargo 1. Nenhum código de eleição é fixado no coletor. Nome de arquivo usa cargo 0001 e código eleitoral com seis dígitos conforme EA20. Diretórios vêm de arq[tp=u], com tokens substituídos e origem HTTPS validada.

Limite informado: 100 requisições/IP/segundo; 304 conta como requisição. 404 repetidos podem causar bloqueio. O coletor serializa 28 downloads por ciclo, não repete 404/429 no ciclo e aguarda 10 minutos após esses códigos; demais falhas aplicam cooldown crescente até 10 minutos. Intervalo mínimo configurável de 15 segundos. Timeout/retry transitório pode alongar a coleta; fixedDelay só conta após o ciclo terminar. Não sondar caminhos inexistentes em paralelo.

## Fixtures oficiais intactas

Baixadas diretamente do TSE em 10/10/2026, geração de resultados em 05/10/2026, fase `o`. **São primeiro turno histórico apenas para testes, não dados do segundo turno.**

| Fixture em backend/src/test/resources/fixtures | Origem |
| --- | --- |
| ele-c.json | https://resultados.tse.jus.br/oficial/comum/config/ele-c.json |
| br-2026-t1.json | https://resultados.tse.jus.br/oficial/ele2026/6257/dados/br/br-c0001-e006257-u.json |
| sp-2026-t1.json | https://resultados.tse.jus.br/oficial/ele2026/6257/dados/sp/sp-c0001-e006257-u.json |
| br-2026-t1-ab.json | https://resultados.tse.jus.br/oficial/ele2026/6257/dados/br/br-e006257-ab.json |

O arquivo `docs/tse/ele-c.json` registra a evidência da descoberta. Fixtures não entram no JAR da aplicação. `checksums.sha256` registra SHA-256 para detectar alterações acidentais; não substitui autenticação criptográfica do TSE.

Validação implementada: fase, eleição, turno, cargo, abrangência, campos obrigatórios, contagens não negativas e sem overflow, percentuais de 0 a 100, IDs únicos, hierarquia de votos e seções, e votação de candidatos. BR inclui exterior (ZZ); não é igual à soma das 27 UFs. Comparação final exige que UFs não excedam BR, sem somar BR às UFs. Reconciliação exata incluindo ZZ e verificação de assinatura JWS não estão implementadas nesta fase.
