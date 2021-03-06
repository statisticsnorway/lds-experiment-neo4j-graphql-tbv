package no.ssb.neo4j.graphql.tbv;

import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.neo4j.graphql.Cypher;
import org.neo4j.graphql.SchemaBuilder;
import org.neo4j.graphql.SchemaConfig;
import org.neo4j.graphql.handler.relation.CreateRelationHandler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TBVSchemas {

    /**
     * Returns a transformed copy of the source-registry. The transformations occur on types that have link directrive
     * set, and will replace this the link directive with a cypher directive capable of resolving time-base-versioning
     * at query time.
     *
     * @param sourceRegistry the type-registry to be transformed. Note that the sourceRegistry instance itself is left
     *                       unchanged, the returned registry is a transformed copy of the source.
     * @return a new type-registry with relevant types transformed to support time-based-versioning
     */
    public static TypeDefinitionRegistry transformRegistry(TypeDefinitionRegistry sourceRegistry) {
        final TypeDefinitionRegistry typeDefinitionRegistry = new TypeDefinitionRegistry().merge(sourceRegistry);
        typeDefinitionRegistry.types().entrySet().forEach(entry -> {
            String nameOfType = entry.getKey();
            if (!(entry.getValue() instanceof ObjectTypeDefinition)) {
                return;
            }
            ObjectTypeDefinition type = (ObjectTypeDefinition) entry.getValue();
            Map<String, FieldDefinition> transformedFields = new LinkedHashMap<>();
            type.getChildren().forEach(child -> {
                if (!(child instanceof FieldDefinition)) {
                    return;
                }
                FieldDefinition field = (FieldDefinition) child;
                if (field.getDirective("link") != null) {

                    String targetType = null;
                    if (field.getType() instanceof ListType) {
                        Type nestedType = ((ListType) field.getType()).getType();
                        if (nestedType instanceof TypeName) {
                            targetType = ((TypeName) nestedType).getName();
                        } else {
                            throw new IllegalArgumentException("Error in " + nameOfType + "." + field.getName() + " : nested list-target type is not a TypeName");
                        }
                    } else if (field.getType() instanceof TypeName) {
                        targetType = ((TypeName) field.getType()).getName();
                    }

                    String relationName = field.getName();

                    String tbvResolutionCypher = String.format("MATCH (this)-[:%s]->(:%s_R:RESOURCE)<-[v:VERSION_OF]-(n:%s:INSTANCE) WHERE v.from <= ver AND coalesce(ver < v.to, true) RETURN n", relationName, targetType, targetType);

                    FieldDefinition transformedField = field.transform(builder -> builder
                            .directives(field.getDirectives()
                                    .stream()
                                    .map(directive -> directive.getName().equals("link") ?
                                            Directive.newDirective()
                                                    .name("cypher")
                                                    .arguments(List.of(Argument.newArgument()
                                                            .name("statement")
                                                            .value(StringValue.newStringValue()
                                                                    .value(tbvResolutionCypher)
                                                                    .build())
                                                            .build()))
                                                    .build() :
                                            directive)
                                    .collect(Collectors.toList()))
                            .inputValueDefinitions(List.of(InputValueDefinition.newInputValueDefinition()
                                    .name("ver")
                                    .type(new TypeName("_Neo4jDateTimeInput"))
                                    .build()))
                    );
                    transformedFields.put(field.getName(), transformedField);
                } else {
                    TypeDefinition typeDefinition = typeDefinitionRegistry.getType(field.getType()).get();
                    if (typeDefinition instanceof ScalarTypeDefinition) {
                    } else if (typeDefinition instanceof EnumTypeDefinition) {
                    } else if (typeDefinition instanceof ObjectTypeDefinition) {
                        FieldDefinition transformedField = field.transform(builder -> builder.directive(Directive.newDirective()
                                .name("relation")
                                .arguments(List.of(Argument.newArgument()
                                        .name("name")
                                        .value(StringValue.newStringValue()
                                                .value(field.getName())
                                                .build())
                                        .build()))
                                .build()
                        ));
                        transformedFields.put(field.getName(), transformedField);
                    } else if (typeDefinition instanceof InterfaceTypeDefinition) {
                        FieldDefinition transformedField = field.transform(builder -> builder.directive(Directive.newDirective()
                                .name("relation")
                                .arguments(List.of(Argument.newArgument()
                                        .name("name")
                                        .value(StringValue.newStringValue()
                                                .value(field.getName())
                                                .build())
                                        .build()))
                                .build()
                        ));
                        transformedFields.put(field.getName(), transformedField);
                    } else if (typeDefinition instanceof UnionTypeDefinition) {
                    } else if (typeDefinition instanceof InputObjectTypeDefinition) {
                    } else {
                        throw new UnsupportedOperationException("Unknown concrete TypeDefinition class: " + typeDefinition.getClass().getName());
                    }
                }
            });

            if (transformedFields.size() > 0) {
                ObjectTypeDefinition transformedObjectType = type.transform(builder ->
                        builder.fieldDefinitions(type.getFieldDefinitions().stream()
                                .map(field -> Optional.ofNullable(transformedFields.get(field.getName())).orElse(field))
                                .collect(Collectors.toList())
                        )
                );
                typeDefinitionRegistry.remove(type);
                typeDefinitionRegistry.add(transformedObjectType);
            }
        });

        return typeDefinitionRegistry;
    }

    /**
     * Returns a GraphQL-schema that will produce cypher mutations and queries compatible with time-based-versioning.
     *
     * @param typeDefinitionRegistry
     * @return the time-based-versioning compatible GraphQL-schema
     */
    public static GraphQLSchema schemaOf(TypeDefinitionRegistry typeDefinitionRegistry) {
        final Set<String> mutationTypes = new CopyOnWriteArraySet<>();
        final Set<String> queryTypes = new CopyOnWriteArraySet<>();

        TypeDefinitionRegistry withoutDomainDirectives = new TypeDefinitionRegistry().merge(typeDefinitionRegistry);
        for (Map.Entry<String, TypeDefinition> typeByName : typeDefinitionRegistry.types().entrySet()) {
            TypeDefinition typeDefinition = typeByName.getValue();
            if (typeDefinition instanceof ObjectTypeDefinition) {
                List<Directive> directives = typeDefinition.getDirectives();
                if (directives.removeIf(d -> d.getName().equals("domain"))) {
                    ObjectTypeDefinition transformedTypeDefinition = ((ObjectTypeDefinition) typeDefinition).transform(builder -> builder.directives(directives));
                    withoutDomainDirectives.remove(typeDefinition);
                    withoutDomainDirectives.add(transformedTypeDefinition);
                }
            }
        }

        GraphQLSchema graphQLSchema = SchemaBuilder.buildSchema(withoutDomainDirectives,
                new SchemaConfig(new SchemaConfig.CRUDConfig(true, Collections.emptyList()), new SchemaConfig.CRUDConfig(true, Collections.emptyList())),
                (dataFetchingEnvironment, dataFetcher) -> {
                    String name = dataFetchingEnvironment.getField().getName();
                    Cypher cypher = dataFetcher.get(dataFetchingEnvironment);
                    if (queryTypes.contains(name)) {
                        String type = unwrapTypeName(dataFetchingEnvironment.getFieldDefinition().getType());
                        cypher.toString();
                        String query = replaceGroup(String.format("MATCH \\(%s:%s\\) WHERE( )", name, type), cypher.component1(), 1, " (_v.from <= $_version AND coalesce($_version < _v.to, true)) AND ");
                        query = replaceGroup(String.format("MATCH (\\(%s:%s\\)) WHERE", name, type), query, 1, String.format("(_r:%s:RESOURCE)<-[_v:VERSION_OF]-(%s:%s:INSTANCE)", type + "_R", name, type));
                        return new Cypher(query, cypher.component2(), cypher.component3());
                    } else if (mutationTypes.contains(name)) {
                        // mutation
                        if (name.startsWith("create")) {
                            String type = unwrapTypeName(dataFetchingEnvironment.getFieldDefinition().getType());
                            int indexOfReturnClause = cypher.component1().lastIndexOf("WITH " + name + " RETURN");
                            StringBuilder sb = new StringBuilder();
                            sb.append("MERGE (r:").append(type).append("_R:RESOURCE {id: $id}) WITH r\n");
                            sb.append("OPTIONAL MATCH (r)<-[v:VERSION_OF {from: $_version}]-(m)-[*]->(e:EMBEDDED) DETACH DELETE m, e WITH r\n");
                            sb.append("OPTIONAL MATCH (r)<-[v:VERSION_OF]-() WHERE v.from <= $_version AND coalesce($_version < v.to, true) WITH r, v AS prevVersion\n");
                            sb.append("OPTIONAL MATCH (r)<-[v:VERSION_OF]-() WHERE v.from > $_version WITH r, prevVersion, min(v.from) AS nextVersionFrom\n");
                            sb.append(cypher.component1(), 0, indexOfReturnClause).append("\n");
                            sb.append("CREATE (r)-[v:VERSION_OF {from: $_version, to: coalesce(prevVersion.to, nextVersionFrom)}]->(").append(name).append(":").append(type).append(":INSTANCE").append(")\n");
                            sb.append("SET prevVersion.to = $_version\n");
                            sb.append(cypher.component1().substring(indexOfReturnClause));
                            return new Cypher(sb.toString(), cypher.component2(), cypher.component3());
                        } else if (name.startsWith("add")) {
                            if (!(dataFetcher instanceof CreateRelationHandler)) {
                                throw new IllegalArgumentException("dataFetcher is not an instance of " + CreateRelationHandler.class.getSimpleName());
                            }
                            String modifiedSourceMatch = cypher.component1();
                            String targetType = ((CreateRelationHandler) dataFetcher).getRelation().getType().getName();
                            String modifiedTargetMatch = replaceGroup(String.format("MATCH \\([^ :)]+:(%s) \\{ [^ :)}]+: \\$[^ })]+ \\}\\)", targetType), modifiedSourceMatch, 1, targetType + "_R:RESOURCE");
                            NavigableSet<String> keys = new TreeSet<>(cypher.component2().keySet());
                            NavigableSet<String> toKeys = keys.subSet("to", true, "to~", false);
                            if (toKeys.size() >= 1) {
                                String firstToKey = toKeys.first();
                                String query = replaceGroup(String.format("MATCH \\([^ :)]+:%s( \\{ [^ :)}]+: \\$[^ })]+ \\}\\))", targetType + "_R:RESOURCE"), modifiedTargetMatch, 1, String.format(") WHERE to.id IN $%s", firstToKey));
                                return new Cypher(query, cypher.component2(), cypher.component3());
                            }
                        } else {
                            throw new UnsupportedOperationException("Only 'create' mutation supported");
                        }
                    }
                    return cypher;
                });

        GraphQLObjectType queryType = graphQLSchema.getQueryType();
        Set<? extends String> originalQueries = queryType.getChildren().stream().map(GraphQLType::getName).collect(Collectors.toSet());
        GraphQLObjectType transformedQueryObject = graphQLSchema.getQueryType().transform(graphQLObjectTypeBuilder -> {
            graphQLObjectTypeBuilder.clearFields();
            for (String originalQuery : originalQueries) {
                GraphQLFieldDefinition originalFieldDefinition = queryType.getFieldDefinition(originalQuery);
                String typeName = unwrapTypeName(originalFieldDefinition.getType());
                TypeDefinition typeDefinition = typeDefinitionRegistry.getType(typeName).get();
                if (typeDefinition.getDirective("domain") != null) {
                    graphQLObjectTypeBuilder.field(originalFieldDefinition.transform(fieldDefinitionBuilder -> {
                    }));
                }
            }
        });

        LinkedHashSet<GraphQLType> newAdditionalTypes = new LinkedHashSet<>(graphQLSchema.getAdditionalTypes());
        if (newAdditionalTypes.contains(graphQLSchema.getQueryType())) {
            newAdditionalTypes.remove(graphQLSchema.getQueryType());
            newAdditionalTypes.add(transformedQueryObject);
        }

        GraphQLSchema transformedGraphQLSchema = GraphQLSchema.newSchema(graphQLSchema).query(transformedQueryObject).clearAdditionalTypes().additionalTypes(newAdditionalTypes).build();

        // set of all query types
        queryTypes.addAll(transformedGraphQLSchema.getQueryType().getChildren().stream().map(GraphQLType::getName).collect(Collectors.toSet()));

        // set of all mutation types
        mutationTypes.addAll(transformedGraphQLSchema.getMutationType().getChildren().stream().map(GraphQLType::getName).collect(Collectors.toSet()));

        return transformedGraphQLSchema;
    }

    private static String unwrapTypeName(GraphQLOutputType type) {
        if (type instanceof GraphQLNonNull) {
            return unwrapTypeName((GraphQLOutputType) type.getChildren().get(0));
        }
        if (type instanceof GraphQLList) {
            return unwrapTypeName((GraphQLOutputType) type.getChildren().get(0));
        }
        if (type instanceof GraphQLObjectType) {
            return type.getName();
        }
        throw new IllegalArgumentException("Unsupported concrete GraphQLOutputType class: " + type.getClass().getName());
    }

    public static String replaceGroup(String regex, String source, int groupToReplace, String replacement) {
        return replaceGroup(regex, source, groupToReplace, 1, replacement);
    }

    public static String replaceGroup(String regex, String source, int groupToReplace, int groupOccurrence, String replacement) {
        Matcher m = Pattern.compile(regex).matcher(source);
        for (int i = 0; i < groupOccurrence; i++)
            if (!m.find()) return source; // pattern not met, may also throw an exception here
        return new StringBuilder(source).replace(m.start(groupToReplace), m.end(groupToReplace), replacement).toString();
    }
}
