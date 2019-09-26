package no.ssb.neo4j.graphql.tbv;

import graphql.schema.GraphQLSchema;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.graphql.SchemaBuilder;
import org.neo4j.graphql.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String sdl = """
                type Person {
                  name: ID!
                  born: Int
                  actedIn(ver:Int): [Movie] @cypher(statement: "MATCH (this)-[:REF]->(:R_Movie)-[v:VERSION]->(r:Movie) WHERE v.from <= ver AND coalesce(ver < v.to, true) RETURN r")
                }
                type Movie {
                  title: ID!
                  released: Int
                  tagline: String
                }""";

        GraphQLSchema graphQLSchema = SchemaBuilder.buildSchema(sdl, new Translator.Context());
        Map<String, ?> params = Map.of("ts", System.currentTimeMillis());
        List<Translator.Cypher> translatedCypher = new Translator(graphQLSchema).translate("""
                {
                  person(first:3) {
                    name
                    born
                    actedIn(first:2, ver:$ts) {
                      title
                    }
                  }
                }""", params);


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
                    StatementResult result = session.run(cypher.component1(), cypher.component2());
                    result.stream().forEachOrdered(record -> {
                        System.out.printf("%s%n", record);
                    });
                });
            }
        }
    }
}
