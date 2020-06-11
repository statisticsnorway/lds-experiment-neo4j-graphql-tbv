package no.ssb.neo4j.graphql.tbv;

import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;
import org.neo4j.graphql.Cypher;
import org.neo4j.graphql.OptimizedQueryException;
import org.neo4j.graphql.SchemaBuilder;
import org.neo4j.graphql.Translator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GsimTest {

    @Test
    public void that() throws IOException, OptimizedQueryException {
        Translator translator = translatorForSchema("schemas/gsim.graphql");

        Map<String, ?> params = Map.of();
        List<Cypher> translatedCypher = translator.translate("""
                {
                  RepresentedVariable(first:3) {
                    shortName
                    name
                    id
                  }
                }""", params);

        System.out.printf("%s%n", translatedCypher);
    }

    private Translator translatorForSchema(String resourcePath) throws IOException {
        String sdl = readResourceAsString(resourcePath);
        GraphQLSchema graphQLSchema = SchemaBuilder.buildSchema(sdl);
        return new Translator(graphQLSchema);
    }

    private String readResourceAsString(String resourcePath) throws IOException {
        CharBuffer cbuf = CharBuffer.allocate(1024 * 1024);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(ClassLoader.getSystemResourceAsStream(resourcePath)), StandardCharsets.UTF_8))) {
            int read = br.read(cbuf);
            while (cbuf.hasRemaining() && read != -1) {
                read = br.read(cbuf);
            }
            if (!cbuf.hasRemaining()) {
                throw new RuntimeException("Resource was larger than 1MB, adjust size of allocated char array and retry");
            }
            cbuf.flip();
        }
        return cbuf.toString();
    }
}
