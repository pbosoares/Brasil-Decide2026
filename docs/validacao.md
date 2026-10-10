# Validação da Fase 1 — 10/10/2026

`mvn -B -f backend/pom.xml verify`: **BUILD SUCCESS**. Java 21, Maven 3.9.9.
15 testes, 0 falhas, 0 erros, 0 ignorados; JAR executável gerado em backend/target/backend-0.1.0.jar.

| Classe | Testes | Evidência |
| --- | --- | --- |
| ApiTest | 1 | contexto Spring, endpoints sem dados retornam 503, UF inválida 404, status/health, Actuator restrito |
| ConfigurationTest | 2 | eleição presidencial do segundo turno descoberta no EA11 real, caminho derivado, ausência rejeitada |
| ResultsServiceTest | 3 | persistência/recuperação, fonte com erro preserva anterior e abre circuito, snapshot incompleto rejeitado |
| TseClientTest | 2 | servidor HTTP local, ETag e 304, 404 sem retry, documento inválido sem contaminar cache |
| TseParserTest | 7 | arquivos oficiais nacional/estadual históricos, identidade/fase, totais, campos ausentes, divulgação, valores negativos, empate/zero |

`docker compose config --quiet`: exit 0. Após iniciar o Docker Desktop (Engine 29.7.2), `docker compose build` concluiu com BUILD SUCCESS e os mesmos 15 testes passaram dentro da imagem de build Linux. `docker compose up -d --wait --wait-timeout 90` terminou com exit 0 e container healthy.

Verificações HTTP reais no container: `/actuator/health` retornou 200 e UP; `/api/v1/results/status` retornou 200, AGUARDANDO_FONTE e desatualizado=true; `/api/v1/results/national` retornou 503 sem snapshot, como previsto. O processo executa como usuário app (não root), `/data` permite gravação e a porta está vinculada apenas a 127.0.0.1. Coleta oficial permanece desativada.

CI criado, mas não executado no GitHub nesta sessão. Não houve coleta contínua ao vivo, deploy de produção ou teste de carga. Sem declaração de capacidade para 100 mil visitantes.

O PDF EA14 não pôde ser lido integralmente por erro de acesso/403; o arquivo JSON oficial correspondente foi preservado para referência. Resolver essa pendência antes de otimizar coleta usando o acompanhamento.

## Fase 2 — frontend

`npm run build`: sucesso na checagem TypeScript e build Vite. Bundle inicial JavaScript: aproximadamente 127 kB gzip; gráfico Recharts carregado sob demanda em chunk separado, aproximadamente 106 kB gzip. Esses tamanhos não constituem teste de capacidade. Avisos não bloqueantes do Rollup referem-se a anotações de comentários no Zod.

`npm test`: 6 testes unitários passaram. `npm run test:e2e` com Chrome instalado: 5 testes de navegador passaram. Cobertura: estado de espera sem votos, mapa com 27 UFs, teclado, seleção, busca sem acento, ordenação, rejeição de snapshot inválido, preservação após erro/reload, aviso de atraso e histórico após duas atualizações. Responsividade sem overflow global em 320, 390, 768 e 1440 px. A tabela conserva sua própria rolagem horizontal em telas estreitas. Capturas desktop/mobile foram inspecionadas visualmente.

Os votos de cenários de navegador são SIMULAÇÃO exclusiva de testes, interceptados em contextos isolados; não há arquivo com votos fictícios no frontend público nem no build. A aplicação aberta em 127.0.0.1:5173 mostra apenas espera, com a fonte ainda não conectada. Publicador CDN e coleta ao vivo não foram ativados. O CI agora inclui build e testes do frontend, mas ainda não foi executado no GitHub.


## Atualização contínua e coerência — 10/10/2026

Build frontend e seis testes de contrato passaram. Oito testes de navegador passaram no Chrome, incluindo timeout com recuperação automática, interação com a página durante a falha, rejeição de uma UF com horário antigo e preservação do snapshot em memória/armazenamento. Os dados de teste continuam exclusivamente interceptados e identificados como simulação.

A imagem Docker do backend foi reconstruída com `mvn verify` concluído com BUILD SUCCESS, incluindo os testes de concorrência entre arquivos e preservação do snapshot completo após falha de uma UF. A prévia responde com o manifest dinâmico do backend e `Cache-Control: no-store`. O coletor foi habilitado na configuração local ignorada pelo Git; nenhuma implantação externa foi realizada. Disponibilidade de novos votos depende da fonte oficial, e essa verificação não é um teste de carga de produção.


Validação final da pré-apuração: 18 testes do backend passaram na imagem Docker (zero falhas/erros), incluindo o arquivo oficial nacional do segundo turno com `and=n`, destinação omitida e situação vazia. Seis testes de contrato do frontend e oito testes de navegador também passaram; build Vite/TypeScript concluído. O estado anterior de falha durante a interpretação dos metadados foi corrigido sem aceitar destinação/situação ausentes durante a apuração.


## Publicador CDN/R2 — 10/10/2026

Imagem Docker reconstruída com AWS SDK oficial 2.55.14 e `mvn verify`: 34 testes do backend passaram, zero falhas/erros. A configuração R2 habilitada foi construída em teste com credenciais fictícias e sem rede; os schedulers da coleta e publicação são distintos. Dez testes do publicador cobrem ordem snapshot/confirmação/manifest, falhas de envio, repetição sem reescrever votos, status com horário da fonte, recuperação sem disco local, escrita concorrente, confirmação corrompida, colisão de versão imutável, manifest de espera e regressão por UF/turno. Quatro testes do adaptador verificam headers/condições S3, payload, ETag, distinção entre 404/403 e redação de credenciais.

Frontend: seis testes de contrato e oito testes de navegador passaram após usar cache HTTP normal para a CDN; build TypeScript/Vite concluído. Docker Compose validado com `config --quiet`. Nenhum DNS, bucket, objeto externo ou recurso da VPS/ecommerce foi alterado. `R2_ENABLED` permanece falso até configurar conta, bucket e credenciais. CORS, DNS/HTTPS e cache do domínio real ainda dependem da ativação conjunta no Cloudflare; os testes locais não os comprovam.
