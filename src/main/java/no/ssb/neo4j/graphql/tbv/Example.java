package no.ssb.neo4j.graphql.tbv;

import java.util.Collections;
import java.util.List;

public interface Example {

    String getSDL();

    default List<QueryAndParams> nativeMutations() {
        return Collections.emptyList();
    }

    List<QueryAndParams> mutations();

    List<QueryAndParams> queries();
}
