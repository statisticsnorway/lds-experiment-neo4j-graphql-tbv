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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws OptimizedQueryException {
        String sdl = """
                type Person {
                  id: ID!
                  name: String
                  address: Address @relation(name:"EMBED")
                  born: _Neo4jDate
                  actedIn(ver:Int): [Movie] @cypher(statement: "MATCH (this)-[:REF]->(:R_Movie)-[v:VERSION]->(r:Movie) WHERE v.from <= ver AND coalesce(ver < v.to, true) RETURN r")
                }
                type Movie {
                  id: ID!
                  title: String
                  released: Int
                  tagline: String
                }
                type Address {
                  street: String
                  city: String
                }""";

        GraphQLSchema graphQLSchema = SchemaBuilder.buildSchema(sdl);
        Translator translator = new Translator(graphQLSchema);

        Map<String, ?> params = Map.of("ts", System.currentTimeMillis(),
                "namePrefix", "Ne",
                "bornLt", LocalDate.of(1980, 1, 1));
        List<Cypher> translatedCypher = translator.translate("""
                {
                  person(first:5, filter: { name_starts_with: $namePrefix, born_lt: $bornLt }) {
                    name
                    born {
                      year
                      month
                      day
                    }
                    address {
                      street
                      city
                    }
                    actedIn(first:5, ver:$ts) {
                      title
                    }
                  }
                }""", params);

        System.out.printf("QUERIES:%n");
        GraphQLObjectType type = graphQLSchema.getQueryType();
        System.out.printf("%s%n", type.toString());
        GraphQLFieldDefinition personQuery = type.getFieldDefinition("person");
        System.out.printf("%s%n", personQuery.toString());
        personQuery.getDirectives();

        List<Cypher> cypherPersonMutation = translator.translate("""
                mutation {
                  createPerson(id: $id, name: $name, born: $born, address: $address) {
                    id
                    name
                    born {
                      year,
                      month
                    }
                  }
                }""", Map.of("id", "ja",
                "name", "Jane Doe",
                "born", Map.of("year", 1977, "month", 4, "day", 23),
                "address", Map.of("street", "First st.", "city", "NY"))
        );

        System.out.printf("MUTATIONS:%n");
        cypherPersonMutation.stream().forEachOrdered(cypher -> {
            System.out.printf("%s%n", cypher.toString());
            String c1 = cypher.component1();
            c1.toString();
        });

        //if (true) return;

        System.out.printf("EXECUTE Cypher:%n");

        try (Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "PasSW0rd"))) {

            try (Session session = driver.session()) {
                session.run("""
                                MERGE (rp : R_Person {id: 'ne'})
                                MERGE (p : Person {name: 'Neo'}) ON CREATE SET p.born = date('1970-04-23')
                                MERGE (p)-[:EMBED]->(pa :Address) ON CREATE SET pa.street = 'first', pa.city = 'NY'
                                MERGE (rp)-[rpv:VERSION]->(p) ON CREATE SET rpv.from = $from
                                MERGE (rm : R_Movie {id: 'ma'})
                                MERGE (m : Movie {title: 'The Matrix'}) ON CREATE SET m.released = '1999', m.tagline = 'Reality is a thing of the past.'
                                MERGE (rm)-[rmv:VERSION]->(m) ON CREATE SET rmv.from = $from
                                MERGE (p)-[r:REF]->(rm)
                                MERGE (rm2: R_Movie {id: 'ma2'})
                                MERGE (m2 : Movie {title: 'The Matrix Reloaded'}) ON CREATE SET m2.released = '2003', m2.tagline = 'We\\'re not here because we\\'re free, we\\'re here because we are not free.'
                                MERGE (rm2)-[rmv2:VERSION]->(m2) ON CREATE SET rmv2.from = $from
                                MERGE (p)-[r2:REF]->(rm2)""",
                        Map.of("from", System.currentTimeMillis())
                );
            }

            try (Session session = driver.session()) {
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
