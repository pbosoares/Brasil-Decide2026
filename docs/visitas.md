# Contador público

Publicado em 10/10/2026 no rodapé de https://brasildecide.pablo-soares.tech/. Build do frontend aprovado e 36 testes do backend aprovados na VPS (incluindo os dois novos testes do contador). GET público validado; visita real pelo navegador exibida no rodapé e total persistido no volume. Exportação validada com o registro inicial `records/visitas-20261010T220745Z.txt` na VPS. Backend e frontend saudáveis, ecommerce respondeu HTTP 200 após a atualização. R2 continua desativado.

Conta uma abertura do documento por carregamento, incluindo recarregamentos. Não mede visitantes únicos, pessoas simultâneas ou tráfego anterior à instalação. Robôs que executem JavaScript e acessos de verificação podem contar. É uma estimativa de acessos registrada pelo aplicativo, não uma auditoria de audiência.

O frontend envia uma identificação aleatória para evitar repetição do efeito do React no mesmo carregamento. Nenhum cookie, IP ou identificação pessoal é gravado pelo contador. O backend aceita a origem do site, valida o identificador e evita repetição dos últimos 10 mil IDs enquanto está em execução. Esse mecanismo reduz repetições acidentais, mas não impede manipulação deliberada por robôs. O Nginx limita requisições e o tamanho do corpo; expõe apenas a rota `/api/v1/visits`, sem abrir a API eleitoral.

O total é gravado atomicamente em `/data/visits.txt`, no mesmo volume persistente do coletor. Somente a gravação bem-sucedida aumenta o valor em memória. Reiniciar ou recriar o backend com o volume preservado mantém a contagem. Não executar `down -v` nem excluir o volume. Uma falha do contador mostra um traço na página e não interfere nos resultados eleitorais.

Após o encerramento da apuração, executar na VPS, dentro de `/opt/brasil-decide-2026`:

```sh
sh deploy/archive-visits.sh
```

O comando salva um arquivo de texto com total e horário UTC em `records/`, sem zerar o contador. Guardar uma cópia fora da VPS antes de excluir qualquer serviço ou volume. O número público continua sendo atualizado com novas visitas; o arquivo registra o total no momento do encerramento. O registro final ainda não foi feito, pois a apuração não aconteceu.

Testes verificam deduplicação, preservação do total ao recriar a classe e rejeição de origem/identificador inválidos. Não há garantia de visitantes únicos nem recuperação histórica anterior à instalação.
