package br.com.brasildecide;

import static br.com.brasildecide.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SnapshotStore {
    private final Path directory;
    private final ObjectMapper mapper;
    public SnapshotStore(TseProperties p, ObjectMapper mapper) { this.directory = p.snapshotDirectory(); this.mapper = mapper; }
    public Optional<Snapshot> load() throws IOException {
        Path current = directory.resolve("current.json");
        return Files.exists(current) ? Optional.of(mapper.readValue(current.toFile(), Snapshot.class)) : Optional.empty();
    }
    public void save(Snapshot snapshot) throws IOException {
        Files.createDirectories(directory);
        byte[] bytes = mapper.writeValueAsBytes(snapshot);
        Path temporary = Files.createTempFile(directory, "snapshot-", ".tmp");
        try {
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) file.write(buffer);
                file.force(true);
            }
            // Same-filesystem atomic rename; fail instead of exposing a partially written file.
            Files.move(temporary, directory.resolve("current.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
