package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** EA11: election and directory resolution, never guessed election paths. */
public record TseConfiguration(Eleicao election, String unifiedDirectory, TseProperties properties) {
    public static TseConfiguration discover(JsonNode root, TseProperties p) {
        String phase = TseParser.required(root, "f");
        if (!phase.equals(p.simulation() ? "s" : "o")) throw new IllegalArgumentException("Fase EA11 incompatível");
        List<TseConfiguration> matches = new ArrayList<>();
        String directory = null;
        for (JsonNode file : TseParser.array(root, "arq"))
            if ("u".equals(file.path("tp").asText())) directory = TseParser.required(file, "dir");
        if (directory == null) throw new IllegalArgumentException("EA11 sem diretório EA20");
        for (JsonNode ballot : TseParser.array(root, "pl")) {
            if (!p.electionDate().format(DateTimeFormatter.ofPattern("dd/MM/uuuu")).equals(ballot.path("dt").asText())) continue;
            for (JsonNode e : TseParser.array(ballot, "e")) {
                if (!Integer.toString(p.round()).equals(e.path("t").asText()) || !"8".equals(e.path("tp").asText())) continue;
                boolean president = false;
                for (JsonNode area : TseParser.array(e, "abr"))
                    if ("br".equals(area.path("cd").asText()))
                        for (JsonNode office : TseParser.array(area, "cp"))
                            if ("1".equals(office.path("cd").asText())) president = true;
                if (president) matches.add(new TseConfiguration(new Eleicao(TseParser.required(e, "cd"),
                        TseParser.required(ballot, "cd"), TseParser.required(ballot, "c"), TseParser.required(e, "nm"),
                        new Turno(p.round(), p.electionDate()), p.simulation()), directory, p));
            }
        }
        if (matches.size() != 1) throw new IllegalArgumentException("Eleição presidencial não disponível ou ambígua no EA11");
        return matches.getFirst();
    }

    public URI resultUri(String uf) {
        if (!uf.matches("br|zz|ac|al|am|ap|ba|ce|df|es|go|ma|mg|ms|mt|pa|pb|pe|pi|pr|rj|rn|ro|rr|rs|sc|se|sp|to"))
            throw new IllegalArgumentException("Abrangência inválida");
        String path = unifiedDirectory.replace("<base>", properties.baseUrl().toString())
                .replace("<ambiente>", properties.environment()).replace("<ciclo>", election.ciclo())
                .replace("<cd_eleicao>", election.codigo()).replace("<cd_pleito>", election.pleito()).replace("<uf>", uf);
        URI uri = URI.create(path + "/" + uf + "-c0001-e%06d-u.json".formatted(Integer.parseInt(election.codigo())));
        if (path.contains("<") || !"https".equals(uri.getScheme()) || !properties.baseUrl().getHost().equals(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getQuery() != null || uri.getPath().contains(".."))
            throw new IllegalArgumentException("Diretório EA11 inseguro ou desconhecido");
        return uri;
    }
}
