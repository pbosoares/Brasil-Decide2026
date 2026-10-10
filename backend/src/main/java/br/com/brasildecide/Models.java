package br.com.brasildecide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Models {
    private Models() {}
    public record Turno(int numero, LocalDate data) {}
    public record Eleicao(String codigo, String pleito, String ciclo, String nome, Turno turno, boolean simulacao) {}
    public record Candidato(String id, String numero, String nome, String partido, long votos,
            BigDecimal percentualOficial, String destinacao, String situacao, boolean indicadoEleitoPeloTse) {}
    public record Totalizacao(long secoes, long secoesTotalizadas, BigDecimal percentual,
            String andamento, boolean totalizacaoFinal) {}
    public record Votos(long total, long validos, long brancos, long nulos, long nulosTecnicos,
            long anulados, long anuladosSubJudice, long semCandidato) {}
    public record Resultado(String uf, String geracao, Instant atualizadoEm, boolean divulgacaoPermitida,
            boolean simulacao, List<Candidato> candidatos, Votos votos, Totalizacao totalizacao,
            String liderId, long diferencaVotos) {}
    public record ResultadoNacional(Resultado resultado) {}
    public record ResultadoEstadual(String uf, Resultado resultado) {}
    public record Snapshot(String versao, Instant coletadoEm, Eleicao eleicao,
            ResultadoNacional nacional, Map<String, ResultadoEstadual> estados) {}
    public record StatusDaFonte(String estado, Instant ultimaTentativa, Instant ultimaVerificacao,
            Instant ultimaAtualizacao, boolean desatualizado, String versao, String fonte, String aviso) {}
}
