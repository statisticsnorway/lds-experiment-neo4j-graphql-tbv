package no.ssb.neo4j.graphql.tbv;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class Neo4JGraphQLJS {

    private final Context c;

    public Neo4JGraphQLJS() {
        c = Context.create("js");
        try {
            File neo4jGraphQLBundleJS = new File(
                    getClass().getClassLoader().getResource("neo4j-graphql-js_bundled.js").getFile());
            c.eval(Source.newBuilder("js", neo4jGraphQLBundleJS).build());
            System.out.println("All functions available from Java (as loaded into Bindings) "
                    + c.getBindings("js").getMemberKeys());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Value makeAugmentedSchema(String typeDefs) {
        Value makeAugmentedSchemaFunction = c.getBindings("js").getMember("makeAugmentedSchema");
        c.asValue(Map.of("typeDefs", typeDefs));
        Value augmentedSchema = makeAugmentedSchemaFunction.execute(Map.of("typeDefs", typeDefs));
        return augmentedSchema;
    }

    public Value makeAugmentedSchemaUsingEval(String typeDefs) {
        try {
            Value augmentedSchema = c.eval(Source.newBuilder("js", String.format("""
                    const typeDefs = `
                    %s`;
                                        
                    const schema = makeAugmentedSchema({ typeDefs });
                    """, typeDefs), "custom").build());
            return augmentedSchema;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        Neo4JGraphQLJS v = new Neo4JGraphQLJS();

        String typeDefs = """
                type Movie {
                    title: String
                    year: Int
                    imdbRating: Float
                    genres: [Genre] @relation(name: "IN_GENRE", direction: "OUT")
                }
                type Genre {
                    name: String
                    movies: [Movie] @relation(name: "IN_GENRE", direction: "IN")
                }
                """;

        Value augmentedSchema = v.makeAugmentedSchemaUsingEval(typeDefs);

        System.out.printf("Augmented JS GraphQL Schema Object:%n%s%n", augmentedSchema);

        Value resolversValue = v.c.eval(Source.newBuilder("js", """                                
                const resolvers = {
                  // entry point to GraphQL service
                  Query: {
                    Movie(object, params, ctx, resolveInfo) {
                      return cypherQuery(params, ctx, resolveInfo);
                    }
                  }
                };
                
                resolvers
                """, "custom").build());

        System.out.printf("resolversValue: %s%n", resolversValue);

        Value cypherValue = v.c.eval(Source.newBuilder("js", """
                
                """, "cypher").build());

        System.out.printf("cypher: %s%n", resolversValue);

    }
}
