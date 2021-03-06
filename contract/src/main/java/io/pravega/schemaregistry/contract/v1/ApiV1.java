/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.schemaregistry.contract.v1;

import com.google.common.annotations.Beta;
import io.pravega.schemaregistry.contract.generated.rest.model.AddedTo;
import io.pravega.schemaregistry.contract.generated.rest.model.CanRead;
import io.pravega.schemaregistry.contract.generated.rest.model.CodecType;
import io.pravega.schemaregistry.contract.generated.rest.model.CodecTypes;
import io.pravega.schemaregistry.contract.generated.rest.model.CreateGroupRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.EncodingId;
import io.pravega.schemaregistry.contract.generated.rest.model.EncodingInfo;
import io.pravega.schemaregistry.contract.generated.rest.model.GetEncodingIdRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.GroupHistory;
import io.pravega.schemaregistry.contract.generated.rest.model.GroupProperties;
import io.pravega.schemaregistry.contract.generated.rest.model.ListGroupsResponse;
import io.pravega.schemaregistry.contract.generated.rest.model.SchemaInfo;
import io.pravega.schemaregistry.contract.generated.rest.model.SchemaVersionsList;
import io.pravega.schemaregistry.contract.generated.rest.model.UpdateCompatibilityRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.Valid;
import io.pravega.schemaregistry.contract.generated.rest.model.ValidateRequest;
import io.pravega.schemaregistry.contract.generated.rest.model.VersionInfo;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

public class ApiV1 {
    @Path("/ping")
    public interface Ping {
        @GET
        Response ping();
    }

    /**
     * Sync Group apis. Identical to {@link GroupsApiAsync}. All methods in this interface are synchronous and return {@link Response} object.
     * The purposes of this interface is to be used by proxy-client.
     */
    @Path("/v1/groups")
    @io.swagger.annotations.Api(description = "the groups API")
    @Beta
    public interface GroupsApi {
        @POST
        @Path("/{groupName}/codecTypes")
        @Consumes({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Adds a new codecType to the group.", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 201, message = "Successfully added codecType to group", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while creating a Group", response = Void.class)})
        Response addCodecType(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace, 
                              @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                              @ApiParam(value = "The codec type", required = true) CodecType codecType);

        @POST
        @Path("/{groupName}/schemas")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Adds a new schema to the group", response = VersionInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 201, message = "Successfully added schema to the group", response = VersionInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 409, message = "Incompatible schema", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 417, message = "Invalid serialization format", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while creating a Group", response = Void.class)})
        Response addSchema(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                           @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                           @ApiParam(value = "Add new schema to group", required = true) SchemaInfo schemaInfo);

        @POST
        @Path("/{groupName}/schemas/versions/canRead")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Checks if given schema can be used for reads subject to compatibility policy in the compatibility.", response = CanRead.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Response to tell whether schema can be used to read existing schemas", response = CanRead.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while checking schema for readability", response = Void.class)})
        Response canRead(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                         @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                         @ApiParam(value = "Checks if schema can be used to read the data in the stream based on compatibility policy.", required = true) SchemaInfo schemaInfo);

        @POST
        @Consumes({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Create a new Group", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 201, message = "Successfully added group", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 409, message = "Group with given name already exists", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while creating a Group", response = Void.class)})
        Response createGroup(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                             @ApiParam(value = "The Group configuration", required = true) CreateGroupRequest createGroupRequest);

        @DELETE
        @Path("/{groupName}")
        @io.swagger.annotations.ApiOperation(value = "", notes = "Delete a Group", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 204, message = "Successfully deleted the Group", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while deleting the Group", response = Void.class)})
        Response deleteGroup(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                             @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName);

        @GET
        @Path("/{groupName}/codecTypes")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get codecTypes for the group.", response = CodecTypes.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found CodecTypes", response = CodecTypes.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group or encoding id with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching codecTypes registered", response = Void.class)})
        Response getCodecTypesList(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                   @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName);

        @GET
        @Path("/{groupName}/encodings/{encodingId}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get the encoding information corresponding to the encoding id.", response = EncodingInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Encoding", response = EncodingInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group or encoding id with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while getting encoding info corresponding to encoding id", response = Void.class)})
        Response getEncodingInfo(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                 @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                 @ApiParam(value = "Encoding id that identifies a unique combination of schema and codecType", required = true) @PathParam("encodingId") Integer encodingId);

        @GET
        @Path("/{groupName}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Fetch the properties of an existing Group", response = GroupProperties.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Group properties", response = GroupProperties.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        Response getGroupProperties(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                    @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName);

        @GET
        @Path("/{groupName}/history")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Fetch the history of schema evolution of a Group", response = GroupHistory.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Group history", response = GroupHistory.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group history", response = Void.class)})
        Response getGroupHistory(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                 @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName);

        @GET
        @Path("/{groupName}/schemas/versions")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get all schema versions for the group", response = SchemaVersionsList.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Versioned history of schemas registered under the group", response = SchemaVersionsList.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        Response getSchemaVersions(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                   @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                   @ApiParam(value = "Type") @QueryParam("type") String type);

        @GET
        @Path("/{groupName}/schemas")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Fetch latest schema versions for all objects identified by SchemaInfo#getType() under a Group. If query param type is specified then latest schema for the type is returned.", response = SchemaVersionsList.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Latest schemas for all objects identified by SchemaInfo#getType() under the group", response = SchemaVersionsList.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        Response getSchemas(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                            @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                            @ApiParam(value = "Type of object") @QueryParam("type") String type);

        @PUT
        @Path("/{groupName}/encodings")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get an encoding id that uniquely identifies a schema version and codec type pair.", response = EncodingId.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Encoding", response = EncodingId.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name or version not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 412, message = "Codec type not registered", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while getting encoding id", response = Void.class)})
        Response getEncodingId(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                               @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                               @ApiParam(value = "Get schema corresponding to the version", required = true) GetEncodingIdRequest getEncodingIdRequest);

        @DELETE
        @Path("/{groupName}/schemas/schema/{schemaId}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Delete schema version from the group.", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 204, message = "Schema corresponding to the version deleted", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while deleting schema from group", response = Void.class)})
        Response deleteSchemaForId(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                   @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,                                    
                                   @ApiParam(value = "version id", required = true) @PathParam("schemaId") Integer version);

        @GET
        @Path("/{groupName}/schemas/schema/{schemaId}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get schema from the version id that uniquely identifies the schema in the group.", response = SchemaInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema corresponding to the version", response = SchemaInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching schema from version", response = Void.class)})
        Response getSchemaForId(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                @ApiParam(value = "version id", required = true) @PathParam("schemaId") Integer schemaId);

        @GET
        @Path("/{groupName}/schemas/format/{serializationFormat}/type/{type}/versions/{version}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get schema from the version id that uniquely identifies the schema in the group.", response = SchemaInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema corresponding to the version", response = SchemaInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching schema from version", response = Void.class)})
        Response getSchemaFromVersion(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                             @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                             @ApiParam(value = "Serialization format", required = true) @PathParam("format") String serializationFormat,
                                             @ApiParam(value = "Schema type from SchemaInfo#getType() or VersionInfo#type", required = true) @PathParam("type") String type,
                                             @ApiParam(value = "Version number", required = true) @PathParam("version") Integer version);

        @DELETE
        @Path("/{groupName}/schemas/format/{serializationFormat}/type/{type}/versions/{version}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Delete schema version from the group.", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 204, message = "Schema corresponding to the version", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while deleting schema from group", response = Void.class)})
        Response deleteSchemaVersion(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                     @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                     @ApiParam(value = "Serialization format", required = true) @PathParam("format") String serializationFormat,
                                     @ApiParam(value = "Schema type from SchemaInfo#getType() or VersionInfo#type", required = true) @PathParam("type") String type,
                                     @ApiParam(value = "Version number", required = true) @PathParam("version") Integer version);

        @POST
        @Path("/{groupName}/schemas/versions/find")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get the version for the schema if it is registered.", response = VersionInfo.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema version", response = VersionInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        Response getSchemaVersion(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                  @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                  @ApiParam(value = "Get schema corresponding to the version", required = true) SchemaInfo schemaInfo);

        @GET
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "List all groups", response = ListGroupsResponse.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "List of all groups", response = ListGroupsResponse.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching the list of Groups", response = Void.class)})
        Response listGroups(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                            @ApiParam(value = "Continuation token") @QueryParam("continuationToken") String continuationToken,
                            @ApiParam(value = "The numbers of items to return") @QueryParam("limit") Integer limit);

        @PUT
        @Path("/{groupName}/compatibility")
        @Consumes({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "update compatibility of an existing Group", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Updated schema compatibility policy", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 409, message = "Write conflict", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while updating Group's compatibility", response = Void.class)})
        Response updateCompatibility(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                     @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                     @ApiParam(value = "update group policy", required = true) UpdateCompatibilityRequest updateCompatibilityRequest);

        @POST
        @Path("/{groupName}/schemas/versions/validate")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Checks if given schema is compatible with schemas in the registry for current policy setting.", response = Valid.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema validation response", response = Valid.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while trying to validate schema", response = Void.class)})
        Response validate(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                          @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                          @ApiParam(value = "Checks if schema is valid with respect to supplied compatibility", required = true) ValidateRequest validateRequest);
    }

    /**
     * ASync Group apis. Identical to {@link GroupsApi}. All methods in this interface are asynchronous and use
     * {@link AsyncResponse}. This is used on service side so that all api implementation is asynchronous.
     */
    @Path("/v1/groups")
    @io.swagger.annotations.Api(description = "the groups API")
    @Beta
    public interface GroupsApiAsync {
        @POST
        @Path("/{groupName}/codecTypes")
        @Consumes({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Adds a new codecType to the group.", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 201, message = "Successfully added codecType to group", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while registering codectype to a Group", response = Void.class)})
        void addCodecType(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                          @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                          @ApiParam(value = "The codec type", required = true) CodecType codecType,
                          @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @POST
        @Path("/{groupName}/schemas")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Adds a new schema to the group", response = VersionInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 201, message = "Successfully added schema to the group", response = VersionInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 409, message = "Incompatible schema", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 417, message = "Invalid serialization format", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while adding a schema", response = Void.class)})
        void addSchema(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                       @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                       @ApiParam(value = "Add new schema to group", required = true) SchemaInfo schemaInfo,
                       @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @POST
        @Path("/{groupName}/schemas/versions/canRead")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Checks if given schema can be used for reads subject to compatibility policy in the compatibility.", response = CanRead.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Response to tell whether schema can be used to read existing schemas", response = CanRead.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while checking schema for readability", response = Void.class)})
        void canRead(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                     @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                     @ApiParam(value = "Checks if schema can be used to read the data in the stream based on compatibility policy.", required = true) SchemaInfo schemaInfo,
                     @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @POST
        @Consumes({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Create a new Group", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 201, message = "Successfully added group", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 409, message = "Group with given name already exists", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while creating a Group", response = Void.class)})
        void createGroup(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                         @ApiParam(value = "The Group configuration", required = true) CreateGroupRequest createGroupRequest,
                         @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @DELETE
        @Path("/{groupName}")
        @io.swagger.annotations.ApiOperation(value = "", notes = "Delete a Group", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 204, message = "Successfully deleted the Group", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while deleting the Group", response = Void.class)})
        void deleteGroup(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                         @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                         @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/codecTypes")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get codecTypes for the group.", response = CodecTypes.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found CodecTypes", response = CodecTypes.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group or encoding id with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching codecTypes registered", response = Void.class)})
        void getCodecTypesList(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                               @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                               @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/encodings/{encodingId}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get the encoding information corresponding to the encoding id.", response = EncodingInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Encoding", response = EncodingInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group or encoding id with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while getting encoding info corresponding to encoding id", response = Void.class)})
        void getEncodingInfo(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                             @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                             @ApiParam(value = "Encoding id that identifies a unique combination of schema and codecType", required = true) @PathParam("encodingId") Integer encodingId,
                             @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Fetch the properties of an existing Group", response = GroupProperties.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Group properties", response = GroupProperties.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        void getGroupProperties(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/history")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Fetch the history of schema evolution of a Group", response = GroupHistory.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Group history", response = GroupHistory.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group history", response = Void.class)})
        void getGroupHistory(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                             @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                             @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/schemas/versions")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get all schema versions for the group.", response = SchemaVersionsList.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Versioned history of schemas registered under the group", response = SchemaVersionsList.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        void getSchemaVersions(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                               @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                               @ApiParam(value = "Type") @QueryParam("type") String type,
                               @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/schemas")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Fetch latest schema versions for all objects identified by SchemaInfo#getType() under a Group. If query param type is specified then latest schema for the type is returned.", response = SchemaVersionsList.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Latest schemas for all objects identified by SchemaInfo#getType() under the group", response = SchemaVersionsList.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group's latest schemas", response = Void.class)})
        void getSchemas(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                        @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                        @ApiParam(value = "Type of object") @QueryParam("type") String type,
                        @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @PUT
        @Path("/{groupName}/encodings")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get an encoding id that uniquely identifies a schema version and codec type pair.", response = EncodingId.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Found Encoding", response = EncodingId.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name or version not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 412, message = "Codec type not registered", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while getting encoding id", response = Void.class)})
        void getEncodingId(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                           @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                           @ApiParam(value = "Get schema corresponding to the version", required = true) GetEncodingIdRequest getEncodingIdRequest,
                           @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/schemas/schema/{schemaId}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get schema from the version id that uniquely identifies the schema in the group.", response = SchemaInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema corresponding to the version", response = SchemaInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching schema from version", response = Void.class)})
        void getSchemaForId(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                            @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                            @ApiParam(value = "version id", required = true) @PathParam("schemaId") Integer schemaId,
                            @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @DELETE
        @Path("/{groupName}/schemas/schema/{schemaId}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Delete schema version from the group.", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 204, message = "Schema corresponding to the version", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while deleting schema from group", response = Void.class)})
        void deleteSchemaForId(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                               @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                               @ApiParam(value = "version id", required = true) @PathParam("schemaId") Integer version,
                               @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Path("/{groupName}/schemas/format/{serializationFormat}/type/{type}/versions/{version}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get schema from the version id that uniquely identifies the schema in the group.", response = SchemaInfo.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema corresponding to the version", response = SchemaInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching schema from version", response = Void.class)})
        void getSchemaFromVersion(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                  @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                  @ApiParam(value = "Serialization format", required = true) @PathParam("serializationFormat") String serializationFormat,
                                  @ApiParam(value = "Schema type from SchemaInfo#getType() or VersionInfo#type", required = true) @PathParam("type") String type,
                                  @ApiParam(value = "Version number", required = true) @PathParam("version") Integer version,
                                  @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @DELETE
        @Path("/{groupName}/schemas/format/{serializationFormat}/type/{type}/versions/{version}")
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Delete schema version from the group.", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 204, message = "Schema corresponding to the version", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while deleting schema from group", response = Void.class)})
        void deleteSchemaVersion(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                 @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                 @ApiParam(value = "Serialization format", required = true) @PathParam("serializationFormat") String serializationFormat,
                                 @ApiParam(value = "Schema type from SchemaInfo#getType() or VersionInfo#type", required = true) @PathParam("type") String type,
                                 @ApiParam(value = "Version number", required = true) @PathParam("version") Integer version,
                                 @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @POST
        @Path("/{groupName}/schemas/versions/find")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Get the version for the schema if it is registered.", response = VersionInfo.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema version", response = VersionInfo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Group details", response = Void.class)})
        void getSchemaVersion(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                              @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                              @ApiParam(value = "Get schema corresponding to the version", required = true) SchemaInfo schemaInfo,
                              @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @GET
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "List all groups", response = ListGroupsResponse.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "List of all groups", response = ListGroupsResponse.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching the list of Groups", response = Void.class)})
        void listGroups(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                        @ApiParam(value = "Continuation token") @QueryParam("continuationToken") String continuationToken,
                        @ApiParam(value = "The numbers of items to return") @QueryParam("limit") Integer limit,
                        @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @PUT
        @Path("/{groupName}/compatibility")
        @Consumes({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "update compatibility of an existing Group", response = Void.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Updated schema compatibility policy", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 409, message = "Write conflict", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while updating Group's compatibility", response = Void.class)})
        void updateCompatibility(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                 @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                                 @ApiParam(value = "update group policy", required = true) UpdateCompatibilityRequest updateCompatibilityRequest,
                                 @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);

        @POST
        @Path("/{groupName}/schemas/versions/validate")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Checks if given schema is compatible with schemas in the registry for current policy setting.", response = Valid.class, tags = {"Group", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema validation response", response = Valid.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Group with given name not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while trying to validate schema", response = Void.class)})
        void validate(@ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                      @ApiParam(value = "Group name", required = true) @PathParam("groupName") String groupName,
                      @ApiParam(value = "Checks if schema is valid with respect to supplied compatibility", required = true) ValidateRequest validateRequest,
                      @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);
    }

    /**
     * Sync Schemas apis. Identical to {@link SchemasApiAsync}. All methods in this interface are synchronous and return {@link Response} object.
     * The purposes of this interface is to be used by proxy-client.
     */
    @Path("/v1/schemas")
    @io.swagger.annotations.Api(description = "the schemas API")
    @Beta
    public interface SchemasApi {
        @POST
        @Path("/addedTo")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Gets a map of groups to version info where the schema if it is registered. SchemaInfo#properties is ignored while comparing the schema.", response = AddedTo.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema version", response = AddedTo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Schema not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Schema references", response = Void.class)})
        Response getSchemaReferences(@ApiParam(value = "Get schema references for the supplied schema", required = true) SchemaInfo schemaInfo,
                                     @ApiParam(value = "namespace") @QueryParam("namespace") String namespace);

    }

    /**
     * Sync Schemas apis. Identical to {@link SchemasApi}. All methods in this interface are asynchronous.
     */
    @Path("/v1/schemas")
    @io.swagger.annotations.Api(description = "the schemas API")
    @Beta
    public interface SchemasApiAsync {
        @POST
        @Path("/addedTo")
        @Consumes({"application/json"})
        @Produces({"application/json"})
        @io.swagger.annotations.ApiOperation(value = "", notes = "Gets a map of groups to version info where the schema if it is registered. SchemaInfo#properties is ignored while comparing the schema.", response = AddedTo.class, tags = {"Schema", })
        @io.swagger.annotations.ApiResponses(value = {
                @io.swagger.annotations.ApiResponse(code = 200, message = "Schema version", response = AddedTo.class),
                @io.swagger.annotations.ApiResponse(code = 404, message = "Schema not found", response = Void.class),
                @io.swagger.annotations.ApiResponse(code = 500, message = "Internal server error while fetching Schema references", response = Void.class)})
        void getSchemaReferences(@ApiParam(value = "Get schema references for the supplied schema", required = true) SchemaInfo schemaInfo,
                                 @ApiParam(value = "namespace") @QueryParam("namespace") String namespace,
                                 @Context SecurityContext securityContext, @Suspended AsyncResponse asyncResponse);
    }
}
