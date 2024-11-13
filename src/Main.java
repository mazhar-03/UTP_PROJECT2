import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Path path = Path.of("server_config.txt");
        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    System.out.println("Key: " + parts[0].trim() + ", Value: " + parts[1].trim());
                } else {
                    System.out.println("Unrecognized line: " + line);
                }
            });
        }catch (IOException e){
            System.err.println("Something went wrong!" + e);
        }
    }
}
