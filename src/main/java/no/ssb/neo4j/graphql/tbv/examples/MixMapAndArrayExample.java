package no.ssb.neo4j.graphql.tbv.examples;

import no.ssb.neo4j.graphql.tbv.Example;
import no.ssb.neo4j.graphql.tbv.QueryAndParams;

import java.util.List;
import java.util.Map;

public class MixMapAndArrayExample implements Example {

    final String SDL = """
            type MixMapAndArray {
              id: ID!
              name: Name
              child: [Child]
            }
            type Child {
              name: String
              born: String
              friend: [Friend]
            }
            type Friend {
              since: String
              link: Person @link
            }
            type Person {
              name: Name
              age: Int
            }
            type Name {
              first: String
              last: String
            }
            """;

    public String getSDL() {
        return SDL;
    }

    public List<QueryAndParams> mutations() {
        return List.of();
    }

    public List<QueryAndParams> queries() {
        return List.of(
                new QueryAndParams("""
                        {
                          mixMapAndArray(first:5, id: $id) {
                            name {
                              first
                            }
                            child {
                              name
                              friend {
                                since
                                link (ver:$_version) {
                                  name {
                                    first
                                    last
                                  }
                                  age
                                }
                              }
                            }
                          }
                        }""",
                        Map.of(
                                "id", "1"
                        )
                )
        );
    }
}
