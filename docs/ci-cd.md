# CI/CD do Brasil Decide 2026

O workflow `.github/workflows/ci.yml` valida pull requests e pushes. O deploy automático fica desativado até configurar `AUTO_DEPLOY=true` e as credenciais abaixo. Não usa dados fictícios em produção.

## Validações obrigatórias

1. Backend: Java 21 e Maven `verify`, incluindo parser, publicação, recuperação e falhas de disco.
2. Frontend: Node 22, `npm ci`, testes de dados, build e Playwright Chromium.
3. Imagens: build de backend/frontend com a revisão Git no rótulo; configuração Compose validada.
4. Smoke test das imagens: filesystem somente leitura, health endpoints, Nginx, ausência de votos sem fonte, recursos estáticos, contador persistente após reinício.
5. Somente a revisão aprovada pelas etapas anteriores pode enviar imagens à VPS. O servidor não compila código durante o deploy.

## Configuração de produção

Criar o environment GitHub `production`, limitado à branch `main`. Proteger `main` contra force push e configurar os checks `frontend`, `backend` e `images` como obrigatórios para merge; se a conta/plano não permitir, essa proteção permanece pendente.

Secrets do environment:

- `VPS_HOST`: `179.199.155.163`.
- `VPS_USER`: usuário autorizado para o comando restrito de deploy.
- `VPS_SSH_KEY`: chave privada exclusiva do pipeline; nunca versionar ou imprimir.
- `VPS_KNOWN_HOSTS`: chave pública do host obtida pelo console autenticado da VPS; não aceitar automaticamente a chave recebida pela rede.

Variáveis do repositório:

- `AUTO_DEPLOY`: `true` somente após instalar e testar o receptor e configurar todos os secrets. Sem ela, os testes executam e o deploy é ignorado.
- `VITE_RESULTS_MANIFEST_URL`: vazio enquanto o site estiver em espera; definir `https://dados.pablo-soares.tech/results/manifest.json` após validar a CDN. É incorporado à imagem durante o build.

Na VPS, instalar `deploy/receive-release.sh` em `/opt/brasil-decide-2026/deploy/receive-release.sh`, propriedade de root e permissão `700`. Requer Bash, Python 3, curl, gzip, tar, flock e Docker Compose v2. O arquivo `deploy/.env.vps` continua exclusivo da VPS e não é enviado ao GitHub.

A chave exclusiva deve ter esta entrada em `authorized_keys`, usando sua chave pública real:

```text
restrict,command="/opt/brasil-decide-2026/deploy/receive-release.sh" ssh-ed25519 CHAVE_PUBLICA brasil-decide-ci
```

Essa chave pode publicar as imagens dos dois serviços do Brasil Decide; não oferece shell interativo, SCP, encaminhamento de portas ou acesso aos outros projetos. Publicar código executável em Docker continua sendo acesso sensível à VPS: limitar a chave ao environment de produção e proteger os workflows e `main`.

## Deploy e rollback

Push em `main` ou execução manual na `main` passa pelo mesmo CI. Há exclusão mútua no GitHub e na VPS; deploys não são cancelados para dar lugar a outro commit. Pull requests nunca recebem credenciais de produção.

O receptor aceita apenas `deploy <SHA de 40 caracteres>`, recebe um pacote limitado a 1 GiB, confere as duas tags e os rótulos de revisão e registra os IDs imutáveis das imagens atualmente em execução. Aplica somente backend/frontend, preservando o volume de snapshots/visitas, as credenciais e o proxy. A saúde dos serviços e o HTTPS público são verificados antes de concluir.

Se a atualização falhar depois de iniciada, o receptor tenta restaurar os IDs das imagens anteriores e retorna erro ao GitHub. Se o rollback também falhar, o log exige intervenção; não existe garantia automática contra falha de disco, indisponibilidade do Docker ou queda da própria VPS.

Depois de um deploy bem-sucedido, comandos manuais devem incluir o override da versão:

```sh
cd /opt/brasil-decide-2026
docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml -f deploy/release.override.yml ps
```

Rollback manual para a versão anterior:

```sh
docker compose --env-file deploy/.env.vps -f deploy/compose.vps.yml -f deploy/rollback.override.yml up -d --no-build --no-deps --wait backend frontend
cp deploy/rollback.override.yml deploy/release.override.yml
```

Não usar `down -v`. Não limpar imagens antigas sem preservar as referenciadas pelo rollback. A alteração do arquivo `.env.vps` para ativar R2 requer recriar o backend com o override da versão atual e executar novo pipeline após atualizar a variável do frontend.

## Pendências de lançamento

Os testes locais não substituem a execução real do GitHub Actions nem o ensaio do deploy. Antes de lançar, confirmar todos os jobs verdes, autenticação restrita, atualização real e recuperação diante de uma falha de saúde. Validar CDN/R2, CORS, cache e manifest público atualizando, conforme `docs/publicacao.md`. Medir capacidade do caminho público em um ensaio controlado; isso ainda não foi realizado.
