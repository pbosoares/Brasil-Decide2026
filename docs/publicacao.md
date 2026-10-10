# Distribuição e deploy planejados

Nada foi publicado ou contratado. O Docker/Compose desta fase serve para operação local e futura instalação controlada na VPS. A imagem foi construída e o container validado localmente em 10/10/2026; a instalação na VPS ainda precisa de validação no ambiente de deploy.

## Mecanismo escolhido para a próxima etapa

Frontend estático no Cloudflare Pages. Snapshots no R2 Standard, distribuídos por domínio próprio e CDN; não fazer deployment do Pages para cada atualização. [Pages tem limites de builds](https://developers.cloudflare.com/pages/platform/limits/). [R2 com domínio próprio permite cache; r2.dev é destinado a desenvolvimento](https://developers.cloudflare.com/r2/buckets/public-buckets/).

Um publicador único deverá ler o snapshot interno, enviar `snapshots/<uuid>.json`, verificar sucesso e somente então trocar `manifest.json` com versão, caminho e timestamps/status. Nunca trocar o manifest antes do objeto. Headers propostos: snapshot `public,max-age=31536000,immutable`; manifest `public,max-age=15,s-maxage=15,stale-while-revalidate=30`. Configurar explicitamente cache de JSON no domínio e validar Age/Cache-Status; R2 não implica cache de JSON por padrão. Manifest precisa de atualização de status mesmo quando votos não mudarem.

Falha no upload preserva manifest anterior. O navegador usa manifest com jitter, baixa apenas versões novas, valida o corpo e preserva a última versão aceita. O atraso deve ser calculado também no cliente; CDN pode continuar servindo um arquivo antigo quando o coletor estiver offline. Retenção proposta: snapshots por 7 dias e último válido sempre preservado; confirmar requisitos antes de implementar lifecycle. Um único publicador e cache no edge reduzem stampede. CDN e publicação R2 ainda são planejamento, não recursos concluídos.

## Custos e capacidade

[Preços R2 consultados em 10/10/2026](https://developers.cloudflare.com/r2/pricing/): Standard US$0,015/GB-mês, US$4,50/milhão operações A, US$0,36/milhão operações B; franquias mensais de 10 GB, 1 milhão A e 10 milhões B. Egress sem cobrança R2. Domínio, Workers se usados, plano de cache e outros serviços podem acrescentar custos; conferir preços antes de contratar.

Com publicação a cada 30 segundos, duas escritas por versão produzem no máximo 5.760 PUTs por dia. Já 100 mil visitantes com polling a cada 30 segundos representam cerca de 3.333 requisições/segundo no edge, além de snapshots novos. Isso é uma estimativa de demanda, **não evidência de capacidade**. Cache hit ratio, distribuição geográfica, limites do plano e volume de leitura na origem precisam ser medidos. Um teste k6 local não comprova 100 mil usuários distribuídos.

## Operação futura na VPS

1. Executar CI e construir imagem; testar saúde, persistência e recuperação após reinício.
2. Configurar DNS/HTTPS para o frontend e domínio dos dados; API permanece em loopback, acessível apenas ao publicador local. Se necessário, acesso operacional por túnel SSH.
3. Habilitar coleta somente após revisar EA11 e disponibilidade dos arquivos do segundo turno, inclusive o PDF EA14 pendente.
4. Criar credencial R2 limitada ao bucket, fora do repositório; usar secrets com rotação. Configurar CORS do domínio dos dados apenas para a origem do frontend, GET/HEAD.
5. Validar publicação atômica de objetos/manifest, cache real, observabilidade e alertas de atraso/disco.
6. Medir com k6 em ambiente autorizado: manifest e snapshots na CDN, cold/hot cache, jitter, falhas, latência p95/p99 e orçamento. Não direcionar testes de carga ao TSE ou à VPS de produção.

Reinicialização automática: `unless-stopped`. Healthcheck verifica processo/API, não frescor. Para a VPS de 4 GB, monitorar RSS, memória direta, CPU e espaço em disco antes de aumentar frequência ou paralelismo. Uma interface de repositório pode substituir SnapshotStore futuramente por PostgreSQL; não adicionar banco até haver necessidade.
