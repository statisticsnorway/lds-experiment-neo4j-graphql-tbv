package no.ssb.neo4j.graphql.tbv.examples;

import no.ssb.neo4j.graphql.tbv.Example;
import no.ssb.neo4j.graphql.tbv.QueryAndParams;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public class PersonMovieExample implements Example {

    final String SDL = """
            type Person {
              id: ID!
              name: String
              address: Address @relation(name: "ADDRESS")
              born: _Neo4jDate
              actedIn: [Movie] @link @relation(name: "ACTED_IN")
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
            }
            """;

    public String getSDL() {
        return SDL;
    }

    public List<QueryAndParams> nativeMutations() {
        return List.of(
                new QueryAndParams("""
                        MERGE (rp : Person_R {id: 'ne'})
                        MERGE (p : Person {name: 'Neo'}) ON CREATE SET p.born = date('1970-04-23')
                        MERGE (p)-[:EMBED]->(pa :Address) ON CREATE SET pa.street = 'first', pa.city = 'NY'
                        MERGE (rp)-[rpv:VERSION]->(p) ON CREATE SET rpv.from = $from
                        MERGE (rm : Movie_R {id: 'ma'})
                        MERGE (m : Movie {title: 'The Matrix'}) ON CREATE SET m.released = '1999', m.tagline = 'Reality is a thing of the past.'
                        MERGE (rm)-[rmv:VERSION]->(m) ON CREATE SET rmv.from = $from
                        MERGE (p)-[r:REF]->(rm)
                        MERGE (rm2: Movie_R {id: 'ma2'})
                        MERGE (m2 : Movie {title: 'The Matrix Reloaded'}) ON CREATE SET m2.released = '2003', m2.tagline = 'We\\'re not here because we\\'re free, we\\'re here because we are not free.'
                        MERGE (rm2)-[rmv2:VERSION]->(m2) ON CREATE SET rmv2.from = $from
                        MERGE (p)-[r2:REF]->(rm2)""",
                        Map.of("from", ZonedDateTime.now(ZoneOffset.UTC))
                )
        );
    }

    public List<QueryAndParams> mutations() {
        return List.of(
                new QueryAndParams("""
                        mutation {
                          createPerson(id: $id, name: $name, born: $born, address: $address) {
                            id
                          }
                        }""",
                        Map.of(
                                "id", "ne",
                                "name", "Neo",
                                "born", Map.of("year", 1970, "month", 4, "day", 23),
                                "address", Map.of("street", "First st.", "city", "NY")
                        )
                ),
                new QueryAndParams("""
                        mutation {
                          createMovie(id: $id, title: $title, released: $released, tagline: $tagline) {
                            id
                          }
                        }""",
                        Map.of(
                                "id", "m1",
                                "title", "The Matrix",
                                "released", 1999,
                                "tagline", "Reality is a thing of the past."
                        )
                ),
                new QueryAndParams("""
                        mutation {
                          createMovie(id: $id, title: $title, released: $released, tagline: $tagline) {
                            id
                          }
                        }""",
                        Map.of(
                                "id", "m2",
                                "title", "The Matrix Reloaded",
                                "released", 2003,
                                "tagline", "We're not here because we're free, we're here because we are not free."
                        )
                ),
                new QueryAndParams("""
                        mutation {
                          addPersonActedIn(id: $id, actedIn: $actedIn) {
                            id
                          }
                        }""",
                        Map.of(
                                "id", "ne",
                                "actedIn", List.of("m1", "m2")
                        )
                )
        );
    }

    public List<QueryAndParams> queries() {
        return List.of(
                new QueryAndParams("""
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
                            actedIn(first:5, ver:$_version) {
                              title
                            }
                          }
                        }""",
                        Map.of(
                                "namePrefix", "Ne",
                                "bornLt", LocalDate.of(1980, 1, 1)
                        )
                )
        );
    }
}
