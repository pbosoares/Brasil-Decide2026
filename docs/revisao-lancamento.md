# Revisão de lançamento — 11/10/2026

Projeto: Brasil Decide 2026. A revisão cobriu recuperação de dados, publicação, seleção estadual, comportamento sem fonte, visitas persistentes e implantação na VPS compartilhada.

## Correções realizadas

- Snapshot recuperado do disco e cada lote novo passam pela validação completa de identidade, abrangência, totais e percentuais antes de substituir o último resultado válido. Uma UF inconsistente invalida o lote inteiro.
- Erro ao salvar o snapshot não avança a versão em memória. Os visitantes continuam recebendo o último lote confirmado.
- Contador corrompido, negativo, ilegível ou fora da faixa deixa apenas o contador indisponível; não impede a inicialização da apuração e não apaga o arquivo.
- Testes de navegador confirmam que Maranhão exibe somente seus percentuais e que a seleção de São Paulo usa os dados dessa UF. Queda de conexão mantém o último resultado com aviso de atraso; reconexão limpa o aviso.
- O seletor de estados recebeu cores próprias para manter contraste no Chrome em tema escuro.
- CI/CD exige testes de backend, frontend, navegador e imagens antes do deploy. A VPS recebe as imagens verificadas; falha de atualização aciona tentativa de rollback para os IDs das imagens anteriores.
- Ubuntu 24.04 fica explícito no workflow para evitar a migração de `ubuntu-latest` antes do lançamento. Actions oficiais foram atualizadas; Java 21 e Node 22 continuam alinhados ao projeto.

## Evidências

Validação local: 39 testes Maven, seis testes de dados do frontend, 11 cenários de navegador Chrome e sete testes do receptor de deploy passaram. Build TypeScript/Vite e sintaxe Bash passaram. `npm audit --omit=dev` não apontou vulnerabilidades. Os cenários de votos simulados existem somente nos testes interceptados.

A primeira execução real do CI passou backend e frontend. O smoke test detectou a ausência de `SNAPSHOT_DIRECTORY=/data` no ambiente de teste Docker, que tentava gravar no filesystem somente leitura. A configuração foi alinhada à produção; o deploy dessa execução foi bloqueado. Consultar o GitHub Actions para o resultado da revisão seguinte.

Chave exclusiva instalada com comando SSH forçado: autenticação confirmada, comando `whoami` rejeitado. Secrets guardados no environment `production`, limitado à `main`. A identidade SSH foi obtida pelo console autenticado da VPS, com verificação estrita no pipeline.

## Condições ainda necessárias para lançamento

O site está publicado em modo de espera. A publicação R2/CDN ainda precisa ser ativada e validada com HTTPS, CORS, TTL, manifest atualizado e horários reais da fonte, conforme `publicacao.md`. Não declarar apuração ao vivo antes dessa confirmação.

Os testes de rollback simulam falhas do Docker e da saúde. Não houve indução de falha de saúde na VPS real. Também não houve teste de carga do caminho público; não há evidência de capacidade para um número específico de visitantes simultâneos.

Antes da apuração, confirmar a execução integral verde do CI/CD, a revisão realmente instalada na VPS, a coleta oficial e a publicação pública atualizando. Manter as imagens do rollback, o volume persistente e as credenciais fora do Git. A automação reduz falhas de implantação; não garante ausência de falhas externas ou de infraestrutura.
