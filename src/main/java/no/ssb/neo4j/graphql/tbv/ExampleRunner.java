package no.ssb.neo4j.graphql.tbv;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.SchemaPrinter;
import org.jetbrains.annotations.NotNull;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.graphql.Cypher;
import org.neo4j.graphql.OptimizedQueryException;
import org.neo4j.graphql.Translator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;

public class ExampleRunner {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    final Example example;
    final GraphQLSchema graphQLSchema;
    final Translator translator;

    public ExampleRunner(Example example) {
        this.example = example;
        this.graphQLSchema = TBVSchemas.schemaOf(TBVSchemas.transformRegistry(new SchemaParser().parse(example.getSDL())));
        this.translator = new Translator(graphQLSchema);
    }

    public void run() {

        System.out.printf("SCHEMA:%n%s%n", serializeSchema(graphQLSchema));

        try (Driver driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "PasSW0rd"))) {

            ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

            runNativeCypher(driver, example.nativeMutations());

            System.out.printf("MUTATIONS:%n");
            translateGraphQLQueryAndRunCypher(driver, nowUtc, example.mutations());

            System.out.printf("QUERIES:%n");
            translateGraphQLQueryAndRunCypher(driver, nowUtc.plusSeconds(1), example.queries());
        }
    }

    private void runNativeCypher(Driver driver, List<QueryAndParams> nativeQueryAndParams) {
        if (nativeQueryAndParams.isEmpty()) {
            return;
        }
        System.out.printf("NATIVE CYPHER:%n");
        try (Session session = driver.session()) {
            nativeQueryAndParams.forEach(queryAndParams -> {
                Result result = session.run(queryAndParams.query, queryAndParams.params);
                // result.stream().forEachOrdered(record -> System.out.printf("%s%n", record));
                result.consume();
            });
        }
    }

    private void translateGraphQLQueryAndRunCypher(Driver driver, ZonedDateTime nowUtc, List<QueryAndParams> listOfQueryAndParams) {
        if (listOfQueryAndParams.isEmpty()) {
            return;
        }
        try (Session session = driver.session()) {
            translateToCypherAndExecute(translator, nowUtc, session, listOfQueryAndParams);
        }
    }

    private void translateToCypherAndExecute(Translator translator, ZonedDateTime timeBasedVersion, Session session, List<QueryAndParams> listOfQueryAndParams) {
        listOfQueryAndParams.forEach(queryAndParams -> {
            try {
                List<Cypher> cyphers = translator.translate(queryAndParams.query, getParamsWithVersionIfMissing(timeBasedVersion, queryAndParams));
                cyphers.stream().forEachOrdered(cypher -> {
                    log.info("{}", cypher.toString());
                    LinkedHashMap<String, Object> params = new LinkedHashMap<>(cypher.component2());
                    params.putIfAbsent("_version", timeBasedVersion);
                    Result result = session.run(cypher.component1(), params);
                    result.stream().forEachOrdered(record -> System.out.printf("%s%n", record));
                    result.consume();
                });
            } catch (OptimizedQueryException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @NotNull
    private LinkedHashMap<String, Object> getParamsWithVersionIfMissing(ZonedDateTime timeBasedVersion, QueryAndParams queryAndParams) {
        LinkedHashMap<String, Object> params = new LinkedHashMap<>(queryAndParams.params);
        params.putIfAbsent("_version", timeBasedVersion);
        return params;
    }

    private static String serializeSchema(GraphQLSchema graphQLSchema) {
        return new SchemaPrinter(SchemaPrinter.Options.defaultOptions()).print(graphQLSchema);
    }
}
