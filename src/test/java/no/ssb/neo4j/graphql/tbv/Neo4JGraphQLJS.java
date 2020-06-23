package no.ssb.neo4j.graphql.tbv;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class Neo4JGraphQLJS {

    private final Context c;

    public Neo4JGraphQLJS() {
        c = Context.create("js");
        try {
            File neo4jGraphQLBundleJS = new File(
                    getClass().getClassLoader().getResource("neo4j-graphql-js_bundled.js").getFile());
            c.eval(Source.newBuilder("js", neo4jGraphQLBundleJS).build());
            System.out.println("All functions available from Java (as loaded into Bindings) "
                    + c.getBindings("js").getMemberKeys());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Value makeAugmentedSchema(String typeDefs) {
        Value makeAugmentedSchemaFunction = c.getBindings("js").getMember("makeAugmentedSchema");
        c.asValue(Map.of("typeDefs", typeDefs));
        Value augmentedSchema = makeAugmentedSchemaFunction.execute(Map.of("typeDefs", typeDefs));
        return augmentedSchema;
    }

    public Value makeAugmentedSchemaUsingEval(String typeDefs) {
        try {
            Value augmentedSchema = c.eval(Source.newBuilder("js", String.format("""
                    const typeDefs = `
                    %s`;
                                        
                    makeAugmentedSchema({ typeDefs });
                    """, typeDefs), "custom").build());
            return augmentedSchema;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException {
        Neo4JGraphQLJS v = new Neo4JGraphQLJS();

        String typeDefs = """
                type Movie {
                    title: String
                    year: Int
                    imdbRating: Float
                    genres: [Genre] @relation(name: "IN_GENRE", direction: "OUT")
                }
                type Genre {
                    name: String
                    movies: [Movie] @relation(name: "IN_GENRE", direction: "IN")
                }
                """;

        Value augmentedSchema = v.makeAugmentedSchemaUsingEval(typeDefs);

        System.out.printf("Augmented JS GraphQL Schema Object:%n%s%n", augmentedSchema);

        Value resolversValue = v.c.eval(Source.newBuilder("js", """
                function xneo4jgraphql(_x, _x2, _x3, _x4, _x5) {
                  return x_neo4jgraphql.apply(this, arguments);
                }
                                    
                function x_neo4jgraphql() {
                  _neo4jgraphql = (0, _asyncToGenerator2["default"])( /*#__PURE__*/_regenerator["default"].mark(function _callee(object, params, context, resolveInfo, debugFlag) {
                    var query, cypherParams, cypherFunction, _cypherFunction, _cypherFunction2, session, result;
                                    
                    return _regenerator["default"].wrap(function _callee$(_context) {
                      while (1) {
                        switch (_context.prev = _context.next) {
                          case 0:
                            if (!(0, _federation.isFederatedOperation)({
                              resolveInfo: resolveInfo
                            })) {
                              _context.next = 6;
                              break;
                            }
                                    
                            _context.next = 3;
                            return (0, _federation.executeFederatedOperation)({
                              object: object,
                              params: params,
                              context: context,
                              resolveInfo: resolveInfo,
                              debugFlag: debugFlag
                            });
                                    
                          case 3:
                            return _context.abrupt("return", _context.sent);
                                    
                          case 6:
                            if (!(0, _auth.checkRequestError)(context)) {
                              _context.next = 8;
                              break;
                            }
                                    
                            throw new Error((0, _auth.checkRequestError)(context));
                                    
                          case 8:
                            if (context.driver) {
                              _context.next = 10;
                              break;
                            }
                                    
                            throw new Error("No Neo4j JavaScript driver instance provided. Please ensure a Neo4j JavaScript driver instance is injected into the context object at the key 'driver'.");
                                    
                          case 10:
                            cypherFunction = (0, _utils.isMutation)(resolveInfo) ? cypherMutation : cypherQuery;
                            _cypherFunction = cypherFunction(params, context, resolveInfo, debugFlag);
                            _cypherFunction2 = (0, _slicedToArray2["default"])(_cypherFunction, 2);
                            query = _cypherFunction2[0];
                            cypherParams = _cypherFunction2[1];
                                    
                            if (debugFlag) {
                              console.log("\\n  Deprecation Warning: Remove `debug` parameter and use an environment variable\\n  instead: `DEBUG=neo4j-graphql-js`.\\n      ");
                              console.log(query);
                              console.log((0, _stringify["default"])(cypherParams, null, 2));
                            }
                                    
                            debug('%s', query);
                            debug('%s', (0, _stringify["default"])(cypherParams, null, 2));
                            context.driver._userAgent = "neo4j-graphql-js/".concat(neo4jGraphQLVersion);
                                    
                            if (context.neo4jDatabase) {
                              // database is specified in context object
                              try {
                                // connect to the specified database
                                // must be using 4.x version of driver
                                session = context.driver.session({
                                  database: context.neo4jDatabase
                                });
                              } catch (e) {
                                // error - not using a 4.x version of driver!
                                // fall back to default database
                                session = context.driver.session();
                              }
                            } else {
                              // no database specified
                              session = context.driver.session();
                            }
                                    
                            _context.prev = 20;
                                    
                            if (!(0, _utils.isMutation)(resolveInfo)) {
                              _context.next = 27;
                              break;
                            }
                                    
                            _context.next = 24;
                            return session.writeTransaction(function (tx) {
                              return tx.run(query, cypherParams);
                            });
                                    
                          case 24:
                            result = _context.sent;
                            _context.next = 30;
                            break;
                                    
                          case 27:
                            _context.next = 29;
                            return session.readTransaction(function (tx) {
                              return tx.run(query, cypherParams);
                            });
                                    
                          case 29:
                            result = _context.sent;
                                    
                          case 30:
                            _context.prev = 30;
                            session.close();
                            return _context.finish(30);
                                    
                          case 33:
                            return _context.abrupt("return", (0, _utils.extractQueryResult)(result, resolveInfo.returnType));
                                    
                          case 34:
                          case "end":
                            return _context.stop();
                        }
                      }
                    }, _callee, null, [[20,, 30, 33]]);
                  }));
                  return _neo4jgraphql.apply(this, arguments);
                }
                                
                const resolvers = {
                  // entry point to GraphQL service
                  Query: {
                    Movie(object, params, ctx, resolveInfo) {
                      return neo4jgraphql(object, params, ctx, resolveInfo);
                    }
                  }
                };
                
                """, "custom").build());

        System.out.printf("resolversValue: %s%n", resolversValue);

    }
}
