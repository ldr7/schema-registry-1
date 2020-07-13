/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.schemaregistry.server.rest.resources;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import io.pravega.auth.AuthException;
import io.pravega.auth.AuthenticationException;
import io.pravega.common.Exceptions;
import io.pravega.schemaregistry.common.FuturesUtility;
import io.pravega.schemaregistry.contract.data.Compatibility;
import io.pravega.schemaregistry.contract.data.GroupProperties;
import io.pravega.schemaregistry.contract.generated.rest.model.CanRead;
import io.pravega.schemaregistry.contract.generated.rest.model.CodecType;
import io.pravega.schemaregistry.contract.generated.rest.model.CodecTypes;
import io.pravega.schemaregistry.contract.generated.rest.model.CreateGroupRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.EncodingId;
import io.pravega.schemaregistry.contract.generated.rest.model.EncodingInfo;
import io.pravega.schemaregistry.contract.generated.rest.model.GetEncodingIdRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.GroupHistory;
import io.pravega.schemaregistry.contract.generated.rest.model.ListGroupsResponse;
import io.pravega.schemaregistry.contract.generated.rest.model.SchemaInfo;
import io.pravega.schemaregistry.contract.generated.rest.model.SchemaVersionsList;
import io.pravega.schemaregistry.contract.generated.rest.model.SchemaWithVersion;
import io.pravega.schemaregistry.contract.generated.rest.model.UpdateCompatibilityRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.Valid;
import io.pravega.schemaregistry.contract.generated.rest.model.ValidateRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.VersionInfo;
import io.pravega.schemaregistry.contract.transform.ModelHelper;
import io.pravega.schemaregistry.contract.v1.ApiV1;
import io.pravega.schemaregistry.exceptions.CodecTypeNotRegisteredException;
import io.pravega.schemaregistry.exceptions.IncompatibleSchemaException;
import io.pravega.schemaregistry.exceptions.PreconditionFailedException;
import io.pravega.schemaregistry.exceptions.SerializationFormatMismatchException;
import io.pravega.schemaregistry.server.rest.ServiceConfig;
import io.pravega.schemaregistry.server.rest.auth.AuthHandlerManager;
import io.pravega.schemaregistry.service.SchemaRegistryService;
import io.pravega.schemaregistry.storage.ContinuationToken;
import io.pravega.schemaregistry.storage.StoreExceptions;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Response;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.pravega.auth.AuthHandler.Permissions.READ;
import static io.pravega.auth.AuthHandler.Permissions.READ_UPDATE;
import static javax.ws.rs.core.Response.Status;

/**
 * Schema Registry Resource implementation.
 */
@Slf4j
public class GroupResourceImpl extends AbstractResource implements ApiV1.GroupsApiAsync {
    private static final int DEFAULT_LIST_GROUPS_LIMIT = 100;
    
    public GroupResourceImpl(SchemaRegistryService registryService, ServiceConfig config, Executor executor) {
        super(registryService, config, executor);
    }

    @Override
    public void listGroups(String namespace, String continuationToken, Integer limit, 
                           AsyncResponse asyncResponse) {
        log.info("List Groups called for namespace {} with limit {} and continuation token {}", namespace, limit, continuationToken);
        int toFetch = limit == null ? DEFAULT_LIST_GROUPS_LIMIT : limit;
        ListGroupsResponse groupsList = new ListGroupsResponse();

        List<String> authorizationHeader = getConfig().isAuthEnabled() ? getAuthorizationHeader() : Collections.emptyList();
        final AuthHandlerManager.Context context;
        if (getConfig().isAuthEnabled()) {
            String credentials = parseCredentials(authorizationHeader);
            try {
                context = getAuthManager().getContext(credentials);
                context.authenticate();
            } catch (AuthenticationException e) {
                log.warn("User authentication failed.", e);
                asyncResponse.resume(Response.status(Response.Status.FORBIDDEN.getStatusCode()).build());
                return;
            }
        } else {
            context = null;
        }

        Predicate<Map.Entry<String, GroupProperties>> authorizedPredicate = x -> {
            try {
                String resource = Strings.isNullOrEmpty(namespace) ?
                        getGroupResource(x.getKey()) :
                        getGroupResource(x.getKey(), namespace);

                return context == null || context.authorize(resource, READ);
            } catch (AuthException e) {
                return false;
            }
        };

        // Get list of groups filtered by list of groups user is authorized on.
        // This wil fetch the groups, and then call authorizationPredicate and if the user is authorized, the result is
        // included, otherwise filtered out. 
        // the filteredWithTokenAndLimit keeps fetching in a loop until it has collected "limit" number of results 
        // or there are no more remaining results to be fetched. 
        CompletableFuture<Map.Entry<ContinuationToken, List<Map.Entry<String, GroupProperties>>>> future = 
                FuturesUtility.filteredWithTokenAndLimit(
                        (ContinuationToken t, Integer l) ->
                                getRegistryService().listGroups(namespace, t, l)
                                                    .thenApply(mwt -> new AbstractMap.SimpleEntry<>(mwt.getToken(),
                                                            new ArrayList<>(mwt.getList()))),
                        authorizedPredicate,
                        ContinuationToken.fromString(continuationToken), toFetch, getExecutorService());

        future.thenAccept(result -> {
            String contToken = result.getKey() == null ?
                    ContinuationToken.EMPTY.toString() : result.getKey().toString();
            groupsList.groups(
                    result.getValue().stream().collect(
                            Collectors.toMap(Map.Entry::getKey, x -> ModelHelper.encode(x.getValue()))))
                      .setContinuationToken(contToken);
        }).thenApply(r -> Response.status(Status.OK).entity(groupsList).build())
              .exceptionally(exception -> {
                  log.warn("listGroups failed with exception: ", Exceptions.unwrap(exception));
                  return Response.status(Status.INTERNAL_SERVER_ERROR).build();
              }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }

    @Override
    public void createGroup(String namespace, CreateGroupRequest createGroupRequest, 
                            AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(createGroupRequest);
        String resource = Strings.isNullOrEmpty(namespace) ? getNamespaceResource() : getNamespaceResource(namespace);
        withAuthenticateAndAuthorize("createGroup", READ_UPDATE, resource, asyncResponse, () -> {
            GroupProperties groupProperties = ModelHelper.decode(createGroupRequest.getGroupProperties());
            String group = createGroupRequest.getGroupName();
            return getRegistryService().createGroup(namespace, group, groupProperties)
                                  .thenApply(createStatus -> {
                                      if (!createStatus) {
                                          log.info("group {} {} already exists", namespace, group);
                                          return Response.status(Status.CONFLICT).build();
                                      }
                                      log.info("group {} {} created", namespace, group);
                                      return Response.status(Status.CREATED).build();
                                  })
                                  .exceptionally(exception -> {
                                      log.warn("createGroup failed with exception: ", Exceptions.unwrap(exception));
                                      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                  });
        }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }

    @Override
    public void getGroupProperties(String namespace, String group, 
                                   AsyncResponse asyncResponse) {
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) : 
                getGroupResource(group, namespace);
        withAuthenticateAndAuthorize("getGroupProperties", READ, resource, asyncResponse,
                () -> getRegistryService().getGroupProperties(namespace, group)
                                     .thenApply(groupProperty -> {
                                         log.info("Group {} {} property found are {}", namespace, group, groupProperty);
                                         return Response.status(Status.OK).entity(ModelHelper.encode(groupProperty)).build();
                                     })
                                     .exceptionally(exception -> {
                                         if (Exceptions.unwrap(exception) instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} not found", namespace, group);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }
                                         log.warn("getGroupProperties for group {} failed with exception: ", group, Exceptions.unwrap(exception));
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }
    
    @Override
    public void getGroupHistory(String namespace, String group, AsyncResponse asyncResponse) {
        log.info("Get group history called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);
        withAuthenticateAndAuthorize("getGroupHistory", READ, resource, asyncResponse,
                () -> getRegistryService().getGroupHistory(namespace, group, null)
                                     .thenApply(history -> {
                                         GroupHistory list = new GroupHistory()
                                                 .history(history.stream().map(ModelHelper::encode)
                                                                 .collect(Collectors.toList()));
                                         log.info("getGroupHistory: {} schemas found for group {} {}", list.getHistory().size(), namespace, group);
                                         return Response.status(Status.OK).entity(list).build();
                                     })
                                     .exceptionally(exception -> {
                                         if (Exceptions.unwrap(exception) instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} not found", namespace, group);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }

                                         log.warn("getGroupHistory failed with exception: ", Exceptions.unwrap(exception));
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void updateCompatibility(String namespace, String group, UpdateCompatibilityRequest updateCompatibilityRequest, 
                                            AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(updateCompatibilityRequest);
        log.info("Update compatibility called for group {} {} with new request {}", namespace, group, updateCompatibilityRequest);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("updateCompatibility", READ_UPDATE, resource, asyncResponse,
                () -> {
                    Compatibility rules = ModelHelper.decode(updateCompatibilityRequest.getCompatibility());
                    Compatibility previous = updateCompatibilityRequest.getPreviousCompatibility() == null ?
                            null : ModelHelper.decode(updateCompatibilityRequest.getPreviousCompatibility());
                    return getRegistryService().updateCompatibility(namespace, group, rules, previous)
                                          .thenApply(groupProperty -> Response.status(Status.OK).build())
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              } else if (unwrap instanceof PreconditionFailedException) {
                                                  log.warn("updateCompatibility write conflict {} {}", namespace, group);
                                                  return Response.status(Status.CONFLICT).build();
                                              } else {
                                                  log.warn("updateCompatibility failed with exception: ", unwrap);
                                                  return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                              }
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }
    
    @Override
    public void deleteGroup(String namespace, String group, 
                            AsyncResponse asyncResponse) {
        log.info("Delete group called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);
        withAuthenticateAndAuthorize("deleteGroup", READ_UPDATE, resource, asyncResponse,
                () -> getRegistryService().deleteGroup(namespace, group)
                                     .thenApply(status -> {
                                         log.info("Group {} {} deleted", namespace, group);
                                         return Response.status(Status.NO_CONTENT).build();
                                     })
                                     .exceptionally(exception -> {
                                         log.warn("deleteGroup failed with exception: ", exception);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void getSchemaVersions(String namespace, String group, String type, AsyncResponse asyncResponse) {
        log.info("Get group schemas called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getSchemaVersions", READ, resource, asyncResponse,
                () -> getRegistryService().getGroupHistory(namespace, group, type)
                                     .thenApply(history -> {
                                         SchemaVersionsList list = new SchemaVersionsList()
                                                 .schemas(history.stream().map(x -> new SchemaWithVersion()
                                                         .schemaInfo(ModelHelper.encode(x.getSchemaInfo()))
                                                         .versionInfo(ModelHelper.encode(x.getVersionInfo())))
                                                                 .collect(Collectors.toList()));
                                         log.info("getSchemaVersions: {} schemas found for group {} {}", list.getSchemas().size(), namespace, group);
                                         return Response.status(Status.OK).entity(list).build();
                                     })
                                     .exceptionally(exception -> {
                                         Throwable unwrap = Exceptions.unwrap(exception);
                                         if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} not found", namespace, group);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }

                                         log.warn("getSchemaVersions failed with exception: ", unwrap);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void addSchema(String namespace, String group, SchemaInfo schemaInfo, 
                                          AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(schemaInfo);
        log.info("Add schema to group called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupSchemaResource(group) :
                getGroupSchemaResource(group, namespace);

        withAuthenticateAndAuthorize("addSchema", READ_UPDATE, resource, asyncResponse,
                () -> {
                    return getRegistryService().addSchema(namespace, group, ModelHelper.decode(schemaInfo))
                                          .thenApply(versionInfo -> {
                                              VersionInfo version = ModelHelper.encode(versionInfo);
                                              log.info("schema added to group {} {} with new version {}", namespace, group, versionInfo);
                                              return Response.status(Status.CREATED).entity(version).build();
                                          })
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              } else if (unwrap instanceof IncompatibleSchemaException) {
                                                  log.info("addSchema incompatible schema for group {} {}", namespace, group);
                                                  return Response.status(Status.CONFLICT).build();
                                              } else if (unwrap instanceof SerializationFormatMismatchException) {
                                                  log.info("addSchema serialization format mismatched for group {} {}", namespace, group);
                                                  return Response.status(Status.EXPECTATION_FAILED).build();
                                              } else {
                                                  log.warn("addSchema failed with exception: ", unwrap);
                                                  return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                              }
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }

    @Override
    public void validate(String namespace, String group, ValidateRequest validateRequest, AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(validateRequest);
        log.info("Validate schema called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("validate", READ, resource, asyncResponse,
                () -> {
                    return getRegistryService().validateSchema(namespace, group, 
                            ModelHelper.decode(validateRequest.getSchemaInfo()),
                            ModelHelper.decode(validateRequest.getCompatibility()))
                                          .thenApply(compatible -> {
                                              log.info("Schema is valid for group {} {}", namespace, group);
                                              return Response.status(Status.OK).entity(new Valid().valid(compatible)).build();
                                          })
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              }
                                              log.warn("validate failed with exception: ", unwrap);
                                              return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }

    @Override
    public void canRead(String namespace, String group, SchemaInfo schemaInfo, AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(schemaInfo);
        log.info("Can read using schema called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("canRead", READ, resource, asyncResponse,
                () -> {
                    return getRegistryService().canRead(namespace, group, ModelHelper.decode(schemaInfo))
                                          .thenApply(canRead -> {
                                              log.info("For group {} {}, can read using schema response = {}", namespace, group, canRead);
                                              return Response.status(Status.OK).entity(new CanRead().compatible(canRead)).build();
                                          })
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              }
                                              log.warn("can read failed with exception: ", unwrap);
                                              return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }

    @Override
    public void getSchemaForId(String namespace, String group, Integer schemaId, AsyncResponse asyncResponse) {
        log.info("Get schema from version {} called for group {} {}", schemaId, namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getSchemaForId", READ, resource, asyncResponse,
                () -> getRegistryService().getSchema(namespace, group, schemaId)
                                     .thenApply(schemaWithVersion -> {
                                         SchemaInfo schema = ModelHelper.encode(schemaWithVersion);
                                         log.info("Schema for version {} for group {} {} found.", schemaId, namespace, group);
                                         return Response.status(Status.OK).entity(schema).build();
                                     })
                                     .exceptionally(exception -> {
                                         Throwable unwrap = Exceptions.unwrap(exception);
                                         if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} or version {} not found", namespace, group, schemaId);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }
                                         log.warn("getSchemaForId failed with exception: ", unwrap);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void getSchemaFromVersion(String namespace, String group, String schemaType, Integer version, AsyncResponse asyncResponse) {
        log.info("Get schema from version {} called for group {} {}", version, namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getSchemaFromVersion", READ, resource, asyncResponse,
                () -> getRegistryService().getSchema(namespace, group, schemaType, version)
                                                                    .thenApply(schemaWithVersion -> {
                                                                        SchemaInfo schema = ModelHelper.encode(schemaWithVersion);
                                                                        log.info("Schema for version {} for group {} {} found.", version, namespace, group);
                                                                        return Response.status(Status.OK).entity(schema).build();
                                                                    })
                                                                    .exceptionally(exception -> {
                                                                        Throwable unwrap = Exceptions.unwrap(exception);
                                                                        if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                                            log.warn("Group {} {} or version {} not found", namespace, group, version);
                                                                            return Response.status(Status.NOT_FOUND).build();
                                                                        }
                                                                        log.warn("getSchemaFromVersion failed with exception: ", unwrap);
                                                                        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                                                    }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void deleteSchemaForId(String namespace, String group, Integer schemaId, 
                                               AsyncResponse asyncResponse) {
        log.info("Delete schema from version {} called for group {} {}", schemaId, namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("deleteSchemaForId", READ_UPDATE, resource, asyncResponse,
                () -> getRegistryService().deleteSchema(namespace, group, schemaId)
                                     .thenApply(v -> {
                                         log.info("Schema for version {} for group {} {} deleted.", schemaId, namespace, group);
                                         return Response.status(Status.NO_CONTENT).build();
                                     })
                                     .exceptionally(exception -> {
                                         Throwable unwrap = Exceptions.unwrap(exception);
                                         if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} or version {} not found", namespace, group, schemaId);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }
                                         log.warn("deleteSchemaForId failed with exception: ", unwrap);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void deleteSchemaVersion(String namespace, String group, String schemaType, Integer version, 
                                    AsyncResponse asyncResponse) {
        log.info("Delete schema from version {}/{} called for group {} {}", schemaType, version, namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupSchemaResource(group) :
                getGroupSchemaResource(group, namespace);

        withAuthenticateAndAuthorize("deleteSchemaVersion", READ_UPDATE, resource, asyncResponse,
                () -> getRegistryService().deleteSchema(namespace, group, schemaType, version)
                                     .thenApply(v -> {
                                         log.info("Schema for version {}/{} for group {} {} deleted.", schemaType, version, namespace, group);
                                         return Response.status(Status.NO_CONTENT).build();
                                     })
                                     .exceptionally(exception -> {
                                         Throwable unwrap = Exceptions.unwrap(exception);
                                         if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} or version {}/{} not found", group, schemaType, version);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }
                                         log.warn("deleteSchemaVersion failed with exception: ", unwrap);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void getEncodingId(String namespace, String group, GetEncodingIdRequest getEncodingIdRequest, 
                              AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(getEncodingIdRequest);
        log.info("getEncodingId called for group {} {} with version {} and codec {}", namespace, group,
                getEncodingIdRequest.getVersionInfo(), getEncodingIdRequest.getCodecType());
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getEncodingId", READ, resource, asyncResponse,
                () -> {
                    io.pravega.schemaregistry.contract.data.VersionInfo version = ModelHelper.decode(getEncodingIdRequest.getVersionInfo());
                    String codecType = getEncodingIdRequest.getCodecType();
                    return getRegistryService().getEncodingId(namespace, group, version, codecType)
                                          .thenApply(encodingId -> {
                                              EncodingId id = ModelHelper.encode(encodingId);
                                              log.info("For group {} {} with version {} and codec {}, returning encoding id {}", namespace, group,
                                                      getEncodingIdRequest.getVersionInfo(), getEncodingIdRequest.getCodecType(), id);
                                              return Response.status(Status.OK).entity(id).build();
                                          })
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              } else if (unwrap instanceof CodecTypeNotRegisteredException) {
                                                  log.info("getEncodingId failed Codec Not Found {} {}", namespace, group);
                                                  return Response.status(Status.PRECONDITION_FAILED).build();
                                              } else {
                                                  log.warn("getEncodingId failed with exception: ", unwrap);
                                                  return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                              }
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }

    @Override
    public void getSchemaVersion(String namespace, String group, SchemaInfo schemaInfo, AsyncResponse asyncResponse) {
        Preconditions.checkNotNull(schemaInfo);
        log.info("Get schema version called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getSchemaVersion", READ, resource, asyncResponse,
                () -> {
                    return getRegistryService().getSchemaVersion(namespace, group, ModelHelper.decode(schemaInfo))
                                          .thenApply(version -> {
                                              VersionInfo versionInfo = ModelHelper.encode(version);
                                              log.info("schema version {} found for group {} {}", versionInfo, namespace, group);
                                              return Response.status(Status.OK).entity(versionInfo).build();
                                          })
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} or schema not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              }

                                              log.warn("getSchemaVersion failed with exception: ", unwrap);
                                              return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }
    
    @Override
    public void getSchemas(String namespace, String group, String type, AsyncResponse asyncResponse) {
        log.info("getSchemas called for group {} {} ", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getSchemas", READ, resource, asyncResponse,
                () -> getRegistryService().getSchemas(namespace, group, type)
                                                          .thenApply(schemas -> {
                                                              SchemaVersionsList schemaList = new SchemaVersionsList()
                                                                      .schemas(schemas.stream().map(ModelHelper::encode).collect(Collectors.toList()));
                                                              List<String> types = schemaList.getSchemas().stream().map(x -> x.getSchemaInfo().getType()).collect(Collectors.toList());
                                                              log.info("Found schemas {} for group {} {} ", types, namespace, namespace, group);
                                                              return Response.status(Status.OK).entity(schemaList).build();
                                                          })
                                                          .exceptionally(exception -> {
                                                              Throwable unwrap = Exceptions.unwrap(exception);
                                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                                  log.warn("Group {} {} not found", namespace, group);
                                                                  return Response.status(Status.NOT_FOUND).build();
                                                              }
                                                              log.warn("getSchemas failed with exception: ", unwrap);
                                                              return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                                          }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void getEncodingInfo(String namespace, String group, Integer encodingId, AsyncResponse asyncResponse) {
        log.info("getEncodingInfo called for group {} {} encodingId {}", namespace, group, encodingId);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getEncodingInfo", READ, resource, asyncResponse,
                () -> {
                    io.pravega.schemaregistry.contract.data.EncodingId id = new io.pravega.schemaregistry.contract.data.EncodingId(encodingId);
                    return getRegistryService().getEncodingInfo(namespace, group, id)
                                          .thenApply(encodingInfo -> {
                                              EncodingInfo encoding = ModelHelper.encode(encodingInfo);
                                              log.info("group {} {} encoding id {} encodingInfo {}", namespace, group, encodingId, encoding);
                                              return Response.status(Status.OK).entity(encoding).build();
                                          })
                                          .exceptionally(exception -> {
                                              Throwable unwrap = Exceptions.unwrap(exception);
                                              if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                                  log.warn("Group {} {} not found", namespace, group);
                                                  return Response.status(Status.NOT_FOUND).build();
                                              }
                                              log.warn("getEncodingInfo failed with exception: ", unwrap);
                                              return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                          });
                }).thenApply(response -> {
            asyncResponse.resume(response);
            return response;
        });
    }


    @Override
    public void getCodecTypesList(String namespace, String group, AsyncResponse asyncResponse) {
        log.info("getCodecTypesList called for group {} {}", namespace, group);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupResource(group) :
                getGroupResource(group, namespace);

        withAuthenticateAndAuthorize("getCodecTypesList", READ, resource, asyncResponse,
                () -> getRegistryService().getCodecTypes(namespace, group)
                                     .thenApply(list -> {
                                         CodecTypes codecsList = new CodecTypes()
                                                 .codecTypes(list.stream().map(ModelHelper::encode).collect(Collectors.toList()));
                                         log.info("group {} {}, codecTypes {} ", namespace, group, codecsList);
                                         return Response.status(Status.OK).entity(codecsList).build();
                                     })
                                     .exceptionally(exception -> {
                                         Throwable unwrap = Exceptions.unwrap(exception);
                                         if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} not found", namespace, group);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }
                                         log.warn("getCodecTypesList failed with exception: ", unwrap);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }

    @Override
    public void addCodecType(String namespace, String group, CodecType codecType, AsyncResponse asyncResponse) {
        log.info("addCodecType called for group {} {} codecType {}", namespace, group, codecType);
        String resource = Strings.isNullOrEmpty(namespace) ? getGroupCodecResource(group) :
                getGroupCodecResource(group, namespace);

        withAuthenticateAndAuthorize("addCodecType", READ, resource, asyncResponse,
                () -> getRegistryService().addCodecType(namespace, group, ModelHelper.decode(codecType))
                                     .thenApply(v -> {
                                         log.info("codecType {} added to group {} {}", codecType, namespace, group);
                                         return Response.status(Status.CREATED).build();
                                     })
                                     .exceptionally(exception -> {
                                         Throwable unwrap = Exceptions.unwrap(exception);
                                         if (unwrap instanceof StoreExceptions.DataNotFoundException) {
                                             log.warn("Group {} {} not found", namespace, group);
                                             return Response.status(Status.NOT_FOUND).build();
                                         }
                                         log.warn("addCodecType failed with exception: ", unwrap);
                                         return Response.status(Status.INTERNAL_SERVER_ERROR).build();
                                     }))
                .thenApply(response -> {
                    asyncResponse.resume(response);
                    return response;
                });
    }
}