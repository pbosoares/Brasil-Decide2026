# Publicação dos dados no Cloudflare R2 e CDN

O publicador está implementado no backend. Bucket, credenciais, domínio e regras do Cloudflare ainda não foram configurados: nenhum objeto foi enviado a uma conta externa nesta etapa. A operação local do coletor continua independente do publicador.

## Funcionamento implementado

1. Captura um snapshot completo e seu status com a mesma versão, sem bloquear a coleta.
2. Revalida segundo turno de 25/10/2026, fase oficial, abrangências, totais, percentuais, liderança e horários.
3. Cria `results/snapshots/<versao>.json` com `If-None-Match: *`. Versões existentes nunca são sobrescritas. O envio inclui Content-MD5 e a leitura de confirmação compara o conteúdo integral.
4. Somente depois da confirmação troca `results/manifest.json`. A escrita condicional usa o ETag anterior para impedir que um publicador concorrente reverta o manifest.
5. Atualiza o status a cada ciclo de publicação, mesmo sem mudança de votos; nesse caso não reenvia o snapshot. Horários são os da fonte, não o upload.
6. Após falha, preserva os objetos válidos e tenta novamente com espera progressiva de 5 até 60 segundos. Um timeout depois do envio do manifest pode significar que a troca ocorreu; a escrita seguinte detecta a mudança do ETag e recupera a versão remota antes de continuar.
7. Ao reiniciar, lê o manifest e o snapshot do bucket. Se o disco local estiver vazio, mantém a última versão pública e marca seu status como indisponível/desatualizado. Rejeita regressão nacional, estadual ou no horário de coleta.

O publicador usa um scheduler separado, com uma execução por vez. O R2 recebe JSON via API S3 autenticada, na região `auto`, com transferência chunked desativada conforme [orientação oficial do Cloudflare](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/). Checksums opcionais CRC do SDK são limitados para compatibilidade; Content-MD5 e a comparação do objeto confirmado continuam ativos. Não há criação automática de bucket, alteração de DNS ou exclusão automática de snapshots.

## Configuração para pablo-soares.tech

Endereço preparado para os dados: `https://dados.pablo-soares.tech/results/manifest.json`. O frontend definitivo pode ter outra origem; a política CORS deve listar exatamente a origem escolhida. Esse endereço ainda não está ativo.

Como o domínio ainda não usa Cloudflare e a VPS também hospeda o ecommerce, a configuração de DNS será feita em conjunto. Antes de trocar nameservers, conferir a cópia de todos os registros atuais, incluindo os usados pelo ecommerce e email. Registros existentes podem permanecer em DNS only durante essa etapa. O subdomínio `dados` será destinado ao bucket; ele não deve apontar para a API do coletor.

A associação de domínio próprio exige que a zona esteja na mesma conta Cloudflare do bucket. A documentação também descreve configuração parcial CNAME; disponibilidade/plano deve ser conferido antes de escolher essa alternativa. [Domínio próprio e cache no R2](https://developers.cloudflare.com/r2/buckets/public-buckets/).

Sequência de ativação:

1. Entrar no Cloudflare e preparar a zona do domínio, preservando os registros existentes.
2. Criar um bucket R2 Standard dedicado aos resultados, por exemplo `brasil-decide-2026`. Conferir as condições de ativação/cobrança da conta antes de confirmar.
3. Criar Access Key ID e Secret Access Key com Object Read & Write somente nesse bucket. O publicador precisa ler para confirmar envios e recuperar a versão anterior. Não precisa de acesso administrativo nem de listar outros buckets.
4. Associar `dados.pablo-soares.tech` em R2 → bucket → Settings → Custom Domains, e aguardar estado Active. `r2.dev` serve para desenvolvimento e não substitui a CDN do domínio próprio.
5. Aplicar [r2-cors.example.json](r2-cors.example.json), substituindo o placeholder pela origem real do frontend. Métodos GET/HEAD; Cache-Control/Pragma permitidos para revalidação; ETag, Age e CF-Cache-Status expostos para diagnóstico. [CORS oficial](https://developers.cloudflare.com/r2/buckets/cors/).
6. Criar Cache Rule para `dados.pablo-soares.tech` e `/results/`, tornando JSON elegível a cache e respeitando os headers da origem. Não impor TTL longo no manifest. Desativar cache de respostas 404/erros nesses caminhos para que um objeto recém-publicado não fique oculto por um erro em cache.
7. Configurar o `.env` privado na máquina do coletor e habilitar o publicador. Na VPS compartilhada, escolher uma porta livre para o projeto (por exemplo `BACKEND_PORT=18080`, depois de conferir disponibilidade), vinculada somente a 127.0.0.1. O publicador funciona dentro do mesmo container; os visitantes não consultam essa porta.

```dotenv
TSE_ENABLED=true
BACKEND_PORT=18080
R2_ENABLED=true
R2_ACCOUNT_ID=<id-da-conta-com-32-caracteres-hexadecimais>
R2_BUCKET=brasil-decide-2026
R2_ACCESS_KEY_ID=<access-key-id>
R2_SECRET_ACCESS_KEY=<secret-access-key>
R2_PREFIX=results/
```

`.env` já está ignorado pelo Git. Guardar as chaves fora de documentos, frontend e chat; na VPS limitar a leitura do arquivo ao operador. Não executar `docker compose config` sem `--quiet` com credenciais configuradas, pois a saída inclui variáveis resolvidas.

Após configurar, executar `docker compose up -d --build --wait` no diretório isolado do Brasil Decide. Não usar compose, volumes, portas ou arquivos do ecommerce. O modo de publicação é desativado por padrão; ativação incompleta falha na inicialização antes de enviar dados.

## Cache e ligação do frontend

- Snapshot: `public,max-age=31536000,immutable`.
- Manifest: `public,max-age=5,s-maxage=5,must-revalidate`.
- Content-Type: `application/json; charset=utf-8`.

No frontend de produção:

```dotenv
VITE_RESULTS_MANIFEST_URL=https://dados.pablo-soares.tech/results/manifest.json
```

Gerar o build depois de configurar a URL. O polling usa o cache HTTP normal e uma URL estável, sem parâmetros aleatórios nem no-cache forçado em cada consulta. O manifest tem validade de cinco segundos no navegador e no edge; isso permite reaproveitar a CDN sem guardar um placar indefinidamente. No domínio dos dados, o Browser Cache TTL também deve respeitar o header da origem, sem uma substituição por horas. A prévia local continua usando o proxy para o backend. O TTL solicitado precisa ser confirmado no domínio real por headers e pela versão servida; alterar o objeto no R2 não invalida imediatamente uma cópia já armazenada no edge. [Consistência e cache do R2](https://developers.cloudflare.com/r2/reference/consistency/).

Checagens após ativar:

```sh
curl -i -H 'Origin: https://ORIGEM-REAL-DO-FRONTEND' https://dados.pablo-soares.tech/results/manifest.json
```

Confirmar HTTPS, CORS, Cache-Control e Age/CF-Cache-Status. Se houver versão, abrir o caminho relativo `arquivo`, validar a versão correspondente e os horários oficiais. Repetir após um ciclo do coletor para conferir que `ultimaVerificacao` avança. Antes da apuração, o manifest terá versão/arquivo nulos e nenhum voto fabricado.

## Operação e validação

Recomendado um único coletor/publicador. A escrita condicional protege contra uma segunda instância acidental, mas não é um mecanismo de operação ativa em múltiplas VPS. Uma falha de publicação é registrada com evento `publication_failed`, tipo e horário da próxima tentativa; mensagens do SDK e credenciais não são registradas pelo publicador. O frescor também é verificado no navegador para detectar um manifest parado mesmo quando a CDN responde HTTP 200.

Não aplicar lifecycle de exclusão até definir retenção e preservar a versão apontada pelo manifest. Esta etapa não executa teste de carga de produção nem comprova disponibilidade para 2 mil usuários simultâneos. Preços e franquias devem ser conferidos na [tabela R2](https://developers.cloudflare.com/r2/pricing/) ao ativar a conta.
