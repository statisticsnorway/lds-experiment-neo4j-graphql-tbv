package no.ssb.neo4j.graphql.tbv;

import java.util.Map;

public class QueryAndParams {

    public final String query;
    public final Map<String, Object> params;

    public QueryAndParams(String query, Map<String, Object> params) {
        this.query = query;
        this.params = params;
    }
}
