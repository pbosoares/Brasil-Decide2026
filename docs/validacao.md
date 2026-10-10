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

`docker compose config --quiet`: exit 0. O daemon Docker não estava ativo; build/container não executados. CI criado, mas não executado no GitHub nesta sessão. Não houve coleta contínua ao vivo, deploy de produção, teste de navegador ou teste de carga. Sem declaração de capacidade para 100 mil visitantes.

O PDF EA14 não pôde ser lido integralmente por erro de acesso/403; o arquivo JSON oficial correspondente foi preservado para referência. Resolver essa pendência antes de otimizar coleta usando o acompanhamento.
