# Contrato dos snapshots públicos — v1

O navegador lê um manifest na CDN e, quando a versão muda, um snapshot imutável. A publicação R2 ainda não foi implementada. O manifest estático incluído no frontend apenas informa que a fonte não está conectada; não inclui votos nem nomes de candidatos.

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

O consumidor aceita exclusivamente `eleicao.turno.numero=2`, `data=2026-10-25`, `ciclo=ele2026`, `simulacao=false`, abrangência BR e todas as 27 UFs. Valida contagens inteiras seguras, totais, divulgação, IDs, diferença e liderança; versões do manifest/snapshot devem coincidir. Não aceita regressão em `coletadoEm`. `fotoUrl` é extensão opcional no candidato, exclusivamente da origem oficial HTTPS do TSE; só enriquecer após descobrir o diretório de fotos pela configuração oficial.

Cada abrangência conserva sua data/hora e ID de geração. BR inclui exterior: não somar BR às UFs. O frontend usa percentuais oficiais, não transforma votos anulados em válidos e não conclui eleição por liderança. `indicadoEleitoPeloTse` deve ser interpretado com a situação oficial; o dashboard exibe a situação textual e usa a totalização final nacional para identificar o fechamento.

## Publicação e fallback

1. Publicador único valida e envia `snapshots/<uuid>.json` ao armazenamento.
2. Confirma envio, então atualiza `manifest.json` com status da fonte e a versão correspondente.
3. Mesmo sem alteração de votos, atualiza periodicamente o status da fonte no manifest. Se o coletor falhar, conserva a última versão e marca indisponível/atrasado.
4. Cache de snapshots: `public,max-age=31536000,immutable`; manifest: TTL curto, com regras de cache de JSON explicitamente verificadas. Consulte [publicação](publicacao.md).

O navegador faz polling após a conclusão da requisição anterior, com jitter de 20–30 segundos e timeout de 12 segundos para o ciclo. Se qualquer etapa falhar, preserva o último snapshot validado. localStorage é opcional; se bloqueado/cheio, o dashboard continua com cache em memória. Ao restaurar dados do armazenamento, considera-os atrasados até receber status válido. Verificação expirada ou apuração parcial sem atualização por mais de dois minutos gera aviso, mesmo com HTTP 200 do cache. Resultado final ainda sinaliza falha de verificação da fonte, mas não é considerado atrasado apenas pela idade de seus votos.

O histórico contém até 30 snapshots observados nesta sessão e descarta datas repetidas por UF. Não promete histórico de períodos anteriores ou persistência dessa série.
