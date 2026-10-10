# Publicação na VPS Hostinger

Publicado em 10/10/2026 em https://brasildecide.pablo-soares.tech/, com HTTPS validado. Registro A `brasildecide` para `179.199.155.163`, TTL 300, criado no DNS Hostinger. R2 permanece desativado.

## Implantação realizada

Pacote transferido ao diretório exclusivo `/opt/brasil-decide-2026`; SHA-256 `3e2c87441f96cff4a18042067f8b8e5d5e63a01393785666dceb692dd919fd6b` confirmado antes da extração. Compose validado na VPS e construído com `up -d --build --wait`; backend e frontend saudáveis. API sem porta pública, frontend ligado à rede `ecommerce_web` e à porta local 18081.

Caddy confirmado no contêiner `ecommerce-web-1`. Configuração original copiada para `deploy/Caddyfile.before` e fonte original para `deploy/Caddyfile.source-before`, ambos na VPS. O bloco do Brasil Decide foi acrescentado à cópia, validado com `caddy validate` e aplicado com `caddy reload`, sem reiniciar o ecommerce. Também foi atualizado o arquivo fonte da release atual `/opt/ecommerce/releases/fd6304c9d514b10b1744ddb36a0c369e1d18f118/deploy/Caddyfile`.

**Persistência do proxy:** o Caddyfile é incorporado na imagem do ecommerce, sem bind mount de configuração. A alteração no contêiner sobrevive a reinício desse contêiner, mas recriar usando a imagem antiga a perde. Antes de recriar o serviço web, reconstruir sua imagem a partir do Caddyfile atualizado. Releases futuras do ecommerce devem incluir o bloco `brasildecide.pablo-soares.tech`; o pipeline desse outro projeto não foi modificado nesta tarefa.

Verificações: Brasil HTTPS HTTP 200 com `ssl_verify_result=0`; HTTP redireciona com 308; ecommerce HTTP 200 antes e depois; backend `/actuator/health` UP; Caddy alcança frontend pela rede; fonte TSE com última verificação preenchida e estado `AGUARDANDO_FONTE`; R2_ENABLED=false. Site público verificado no navegador com fotos e nomes, mapa e placar sem votos. O manifest público ainda é o arquivo estático de espera, até ativar a CDN em 24/10. Não houve teste de carga.

## Infraestrutura identificada

Inspeção do hPanel em 10/10/2026: VPS Ubuntu 26.04 LTS, KVM 1, 1 CPU / 4 GB RAM, IP `179.199.155.163`. Projeto Docker `ecommerce`, três contêineres `db`, `api` e `web`. O serviço web ocupa 80/TCP, 443/TCP e 443/UDP, usa volumes `caddy_data`, `caddy_config` e rede `ecommerce_web`. Esses volumes indicam Caddy; confirmar seu Dockerfile/Caddyfile antes de alterar o roteamento. O painel mostrou um aviso de YAML não processado; nenhuma edição foi salva.

Site proposto: `brasildecide.pablo-soares.tech`. Os dados continuam reservados para `dados.pablo-soares.tech` no R2, com ativação combinada para 24/10/2026.

## Arquivos preparados

- `deploy/compose.vps.yml`: projeto separado `brasil-decide-2026`, volume próprio, limites de memória/CPU e logs; API sem porta pública. Frontend na porta local 18081 e na rede existente do proxy com alias exclusivo `brasil-decide-frontend`.
- `frontend/Dockerfile`: build reproduzível pelo lockfile, Nginx sem root, healthcheck e cache de assets. Não inclui `.env` no contexto.
- `deploy/Caddyfile.site.example`: bloco adicional para o Caddy existente encaminhar ao frontend. Não substituir o Caddyfile inteiro.
- `deploy/.env.vps.example`: R2 desativado; fonte oficial habilitada. Site estático mostra espera até a URL da CDN ser configurada e o frontend reconstruído.

O modelo `deploy/nginx-host.conf.example` é apenas alternativa para Nginx no host; não usar nessa VPS sem necessidade, pois 80/443 já pertencem ao ecommerce. Se o proxy for confirmado como Caddy, usar o bloco Caddy.

## Antes de iniciar

Pelo SSH ou Web console do hPanel, conferir sem modificar serviços:

```sh
docker ps --format 'table {{.Names}}\t{{.Ports}}'
docker network inspect ecommerce_web --format '{{.Name}}'
ss -lnt | grep ':18081 '
free -h
df -h / /var/lib/docker
```

A última porta deve estar livre. Ajustar `FRONTEND_PORT` se necessário. Conferir memória disponível com o ecommerce em funcionamento: os limites dos novos contêineres somam aproximadamente 1,4 GB; o build também consome recursos. Se houver pouca folga, construir as imagens fora da VPS e transferi-las antes de subir os serviços.

Copiar os fontes para diretório exclusivo, como `/opt/brasil-decide-2026`, sem copiar `.env`, `node_modules`, `.git` ou credenciais locais. O compose precisa dos diretórios backend e frontend; colar somente o YAML no hPanel não fornece os fontes para o build.

## Subir o projeto separado

No diretório exclusivo com os fontes completos:

```sh
cp deploy/.env.vps.example deploy/.env.vps
chmod 600 deploy/.env.vps
docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml config --quiet
docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml up -d --build --wait
curl --fail http://127.0.0.1:18081/healthz
curl --fail http://127.0.0.1:18081/results/manifest.json
docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml exec -T backend wget -qO- http://127.0.0.1:8080/actuator/health
```

Nunca executar `docker compose config` sem `--quiet` depois de inserir as chaves R2. Não executar `down -v` nem comandos dentro do projeto ecommerce.

## DNS e HTTPS

No DNS atual da Hostinger, criar um registro A apenas para `brasildecide`, apontando a `179.199.155.163`. Conferir previamente se esse nome está livre e se existe AAAA conflitante; não alterar domínio raiz, ecommerce, email ou nameservers.

Localizar e salvar cópia do Caddyfile atual. Acrescentar o bloco do exemplo, validar com `caddy validate` usando o caminho real e aplicar `caddy reload` no contêiner web. Se o arquivo estiver incorporado na imagem, identificar como o ecommerce o constrói antes de decidir a aplicação; não reinstalar proxy nem recriar o ecommerce às cegas. Caddy providencia HTTPS após DNS e conectividade corretos. Conferir o ecommerce antes e depois da mudança.

## Ativação e encerramento do R2

Em 24/10, configurar bucket, domínio, CORS e cache conforme `publicacao.md`. Inserir chaves somente em `deploy/.env.vps`, habilitar R2 e definir:

```dotenv
R2_ENABLED=true
VITE_RESULTS_MANIFEST_URL=https://dados.pablo-soares.tech/results/manifest.json
```

Reexecutar o compose com `--build --wait`; a URL do frontend é definida durante o build. Antes disso não haverá placar público vivo, embora o coletor possa consultar o TSE e persistir o último snapshot no volume.

Ao fim da apuração, preservar resultado final e apontar o site para uma cópia estática validada antes de cancelar o R2. Essa migração final ainda precisa ser executada; cancelar o bucket enquanto o frontend o consulta interrompe o acesso aos resultados.

## Validação local

Após autorização, `docker compose ... config --quiet` passou e a imagem `brasil-decide-frontend:vps-check` foi construída. O contêiner iniciou com filesystem somente leitura, tmpfs, limite de 128 MB/0,25 CPU e capabilities removidas. `nginx -t` passou; `/healthz` e `/results/manifest.json` responderam HTTP 200. O manifest de espera tem versão nula e `Cache-Control: no-store`.

O npm identificou três alertas associados ao Vitest e suas dependências de desenvolvimento. Não são incluídos na imagem final Nginx, que contém apenas os assets compilados; sua correção nas ferramentas de teste continua pendente. Não há teste de carga, validação do Compose inteiro na rede real da VPS ou validação HTTPS/DNS real nesta preparação.

`npm audit --omit=dev --json` retornou zero alertas nas dependências de produção. O contêiner temporário foi removido após as verificações.
