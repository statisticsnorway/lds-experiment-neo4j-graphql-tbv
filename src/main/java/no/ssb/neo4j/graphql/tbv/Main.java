package no.ssb.neo4j.graphql.tbv;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.graphql.Cypher;
import org.neo4j.graphql.OptimizedQueryException;
import org.neo4j.graphql.SchemaBuilder;
import org.neo4j.graphql.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws OptimizedQueryException {
        String sdl = """
                type Person {
                  id: ID!
                  name: String
                  born: _Neo4jDateTime
                  actedIn(ver:Int): [Movie] @cypher(statement: "MATCH (this)-[:REF]->(:R_Movie)-[v:VERSION]->(r:Movie) WHERE v.from <= ver AND coalesce(ver < v.to, true) RETURN r")
                }
                type Movie {
                  id: ID!
                  title: String
                  released: Int
                  tagline: String
                }""";

        GraphQLSchema graphQLSchema = SchemaBuilder.buildSchema(sdl);
        Translator translator = new Translator(graphQLSchema);

        Map<String, ?> params = Map.of("ts", System.currentTimeMillis());
        List<Cypher> translatedCypher = translator.translate("""
                {
                  person(first:3) {
                    name
                    born {
                      year
                      month
                    }
                    actedIn(first:2, ver:$ts) {
                      title
                    }
                  }
                }""", params);

        GraphQLObjectType type = graphQLSchema.getQueryType();
        System.out.printf("%s%n", type.toString());
        GraphQLFieldDefinition personQuery = type.getFieldDefinition("person");
        System.out.printf("%s%n", personQuery.toString());
        personQuery.getDirectives();

        List<Cypher> cypherPersonMutation = translator.translate("""
                mutation {
                  createPerson(id: $id, name: $name, born: $born) {
                    id
                    name
                    born {
                      year,
                      month
                    }
                  }
                }""", Map.of("id", "ja",
                "name", "Jane Doe",
                "born", Map.of("year", 1977, "month", 4))
        );

        cypherPersonMutation.stream().forEachOrdered(cypher -> {
            System.out.printf("%s%n", cypher.toString());
            String c1 = cypher.component1();
            c1.toString();
        });

        //if (true) return;

        try (Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "PasSW0rd"))) {

            try (Session session = driver.session()) {
                session.run("""
                    MERGE (rp : R_Person {id: 'ne'})
                    MERGE (p : Person {name: 'Neo'}) ON CREATE SET p.born = 1970
                    MERGE (rp)-[rpv:VERSION]->(p) ON CREATE SET rpv.from = $from
                    MERGE (rm : R_Movie {id: 'ma'})
                    MERGE (m : Movie {title: 'The Matrix'}) ON CREATE SET m.released = '1999', m.tagline = 'Reality is a thing of the past.'
                    MERGE (rm)-[rmv:VERSION]->(m) ON CREATE SET rmv.from = $from
                    MERGE (p)-[r:REF]->(rm)""",
                        Map.of("from", System.currentTimeMillis())
                );

                translatedCypher.stream().forEachOrdered(cypher -> {
                    log.info("{}", cypher.toString());
                    Result result = session.run(cypher.component1(), cypher.component2());
                    result.stream().forEachOrdered(record -> {
                        System.out.printf("%s%n", record);
                    });
                });
            }
        }
    }
}
