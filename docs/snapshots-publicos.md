# Contrato dos snapshots públicos — v1

O navegador lê um manifest na CDN e, quando a versão muda, um snapshot imutável. A publicação R2 foi implementada e testada localmente; conta, bucket, domínio e cache externos ainda precisam ser configurados. O manifest estático incluído no frontend apenas informa que a fonte não está conectada; não inclui votos nem nomes de candidatos.

## Manifest

```json
{
  "schemaVersion": 1,
  "versao": null,
  "arquivo": null,
  "status": {
    "estado": "AGUARDANDO_FONTE",
    "ultimaTentativa": null,
    "ultimaVerificacao": null,
    "ultimaAtualizacao": null,
    "desatualizado": true,
    "versao": null,
    "fonte": "TSE",
    "aviso": "Projeto independente, sem vínculo com a Justiça Eleitoral."
  }
}
```

Com dados reais, `versao` recebe o UUID do snapshot, `arquivo` recebe `snapshots/<uuid>.json` e `status.versao` deve ser idêntico. Datas/status devem vir da API interna do coletor, jamais do horário de upload. A URL do arquivo é relativa ao diretório do manifest e deve permanecer na mesma origem. O frontend rejeita caminhos que não correspondam a `snapshots/[a-zA-Z0-9_-]+.json`.

O conteúdo do snapshot tem o formato de `Models.Snapshot`: `versao`, `coletadoEm`, `eleicao`, `nacional: {resultado}`, `estados: {AC: {uf, resultado}, ...}`. Usar o snapshot único do coletor; não montar uma versão lendo endpoints separados durante uma troca de dados. Ver [contrato da API](api.md).

O consumidor aceita exclusivamente `eleicao.turno.numero=2`, `data=2026-10-25`, `ciclo=ele2026`, `simulacao=false`, abrangência BR e todas as 27 UFs. Valida contagens inteiras seguras, totais, divulgação, IDs, diferença e liderança; versões do manifest/snapshot devem coincidir. Não aceita regressão em `coletadoEm` nem nos horários do resultado nacional ou de qualquer UF. `fotoUrl` é extensão opcional no candidato, exclusivamente da origem oficial HTTPS do TSE; só enriquecer após descobrir o diretório de fotos pela configuração oficial.

Cada abrangência conserva sua data/hora e ID de geração. BR inclui exterior: não somar BR às UFs. O frontend usa percentuais oficiais, não transforma votos anulados em válidos e não conclui eleição por liderança. `indicadoEleitoPeloTse` deve ser interpretado com a situação oficial; o dashboard exibe a situação textual e usa a totalização final nacional para identificar o fechamento.

## Publicação e fallback

1. Publicador único valida e envia `snapshots/<uuid>.json` ao armazenamento.
2. Confirma envio, então atualiza `manifest.json` com status da fonte e a versão correspondente.
3. Mesmo sem alteração de votos, atualiza periodicamente o status da fonte no manifest. Se o coletor falhar, conserva a última versão e marca indisponível/atrasado.
4. Cache de snapshots: `public,max-age=31536000,immutable`; manifest: `public,max-age=5,s-maxage=5,must-revalidate`, com regras de cache de JSON a verificar no domínio real. Consulte [publicação](publicacao.md).

O navegador faz polling após a conclusão da requisição anterior: 5–6 segundos com a página visível, 30–31 segundos em segundo plano e timeout de 12 segundos para o ciclo. Falhas usam tentativas progressivas de 2 até 30 segundos com jitter. Ao recuperar a conexão ou voltar à página, consulta imediatamente, sem requisições sobrepostas. Se qualquer etapa falhar, preserva o último snapshot validado. localStorage é opcional; se bloqueado/cheio, o dashboard continua com cache em memória. Ao restaurar dados do armazenamento, considera-os atrasados até receber status válido. Verificação expirada ou apuração parcial sem atualização por mais de dois minutos gera aviso, mesmo com HTTP 200 do cache. Resultado final ainda sinaliza falha de verificação da fonte, mas não é considerado atrasado apenas pela idade de seus votos.

O histórico contém até 30 snapshots observados nesta sessão e descarta datas repetidas por UF. Não promete histórico de períodos anteriores ou persistência dessa série.


## Integração local e atualização da fonte

O servidor Vite encaminha `/results/*` para `/api/v1/results/*` no backend em 127.0.0.1:8080. O backend disponibiliza `manifest.json` com `no-store` e o snapshot completo correspondente. Se uma nova versão substituir a anterior entre as duas requisições, responde 409 e o navegador tenta novamente mantendo o placar anterior. Essa ponte local não substitui o publicador CDN para produção; o build estático continua exigindo um manifest publicado.

O coletor verifica BR antes de consultar as UFs, evitando consultar todos os estados quando o arquivo nacional ainda não está disponível. Lê as UFs com no máximo quatro requisições simultâneas, com prazo de 45 segundos para a etapa de resultados. Qualquer arquivo inválido, ausente, atrasado em relação ao snapshot anterior ou com erro impede a publicação do novo lote completo. A API continua respondendo com o snapshot anterior enquanto a coleta acontece. As consultas condicionais preservam ETag/Last-Modified somente depois da validação, com bloqueio por URL, sem bloquear outras UFs.

A frequência de consulta do navegador não é uma promessa de novos votos a cada cinco segundos: a coleta é periódica (30 segundos após o ciclo) e a publicação de votos depende do TSE. Uma fonte que não publica novos resultados não pode ser compensada por estimativa ou interpolação.


Quando o arquivo nacional oficial informa `and=n` (apuração não iniciada), o coletor valida sua estrutura e mantém `AGUARDANDO_FONTE`, sem publicar um placar de zeros. Campos de situação/destinação ausentes ou vazios só são aceitos se o arquivo indicar pré-apuração, nenhuma seção totalizada, zero votos e totalização não final. Quando a apuração começa, esses campos voltam a ser obrigatórios. UFs ainda não iniciadas podem compor o lote completo, mas seus percentuais de candidaturas permanecem em espera e a idade de sua geração não é tratada como atraso de uma apuração em andamento.

Fixture `br-2026-t2-waiting.json`: resposta oficial nacional do segundo turno obtida em 10/10/2026, `ele=6258`, fase oficial, `and=n`, zero votos. Uso exclusivo em testes, sem inclusão no frontend público.
