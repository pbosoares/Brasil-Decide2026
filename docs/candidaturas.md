# Identificação e fotos das candidaturas

Os nomes Lula (13, PT) e Flavio Bolsonaro (22, PL) aparecem antes da apuração, conforme solicitado. A identificação está separada dos resultados: não contém votos, percentuais ou liderança. Dados do primeiro turno não alimentam o placar.

O TSE confirmou as duas candidaturas no segundo turno em https://www.tse.jus.br/comunicacao/noticias/2026/Outubro/flavio-bolsonaro-e-lula-vao-disputar-o-2o-turno-para-a-presidencia-da-republica . Números e partidos constam no portal de planos de governo do TSE.

Fotos oficiais obtidas em 10/10/2026, preservadas sem alteração em `frontend/public/candidates/`:

- Lula, cadastro `280002542548`: https://resultados.tse.jus.br/oficial/ele2026/6257/fotos/br/280002542548.jpeg
- Flavio Bolsonaro, cadastro `280002551544`: https://resultados.tse.jus.br/oficial/ele2026/6257/fotos/br/280002551544.jpeg

O diretório de fotos segue o contrato publicado pelo TSE em `ele-c.json`. As fotos de cadastro disponíveis no diretório do primeiro turno são usadas apenas como retratos das mesmas pessoas. As imagens são servidas localmente, sem requisição ao TSE pelo navegador.

As cores são associadas ao identificador oficial da candidatura, independentemente da ordenação ou votação: Lula `#e45359`, Flavio `#4c8de8`. Uma UF só recebe essa cor quando há resultado divulgado com liderança; empate e ausência de dados permanecem neutros.
