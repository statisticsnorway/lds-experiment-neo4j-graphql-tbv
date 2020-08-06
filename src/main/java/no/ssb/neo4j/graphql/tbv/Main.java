package no.ssb.neo4j.graphql.tbv;

import no.ssb.neo4j.graphql.tbv.examples.PersonMovieExample;

public class Main {

    public static void main(String[] args) {
        new ExampleRunner(new PersonMovieExample()).run();
    }
}
