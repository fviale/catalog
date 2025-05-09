/*
 * ProActive Parallel Suite(TM):
 * The Open Source library for parallel and distributed
 * Workflows & Scheduling, Orchestration, Cloud Automation
 * and Big Data Analysis on Enterprise Grids & Clouds.
 *
 * Copyright (c) 2007 - 2017 ActiveEon
 * Contact: contact@activeeon.com
 *
 * This library is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation: version 3 of
 * the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * If needed, contact us to obtain a release under GPL Version 2 or 3
 * or a different license than the AGPL.
 */
package org.ow2.proactive.catalog.rest.controller;

import static org.ow2.proactive.catalog.util.AccessType.admin;
import static org.springframework.web.bind.annotation.RequestMethod.*;

import java.util.List;

import org.ow2.proactive.catalog.dto.BucketGrantMetadata;
import org.ow2.proactive.catalog.service.BucketGrantService;
import org.ow2.proactive.catalog.service.GrantRightsService;
import org.ow2.proactive.catalog.service.RestApiAccessService;
import org.ow2.proactive.catalog.service.exception.AccessDeniedException;
import org.ow2.proactive.catalog.service.exception.BucketGrantAccessException;
import org.ow2.proactive.catalog.service.exception.LostOfAdminGrantRightException;
import org.ow2.proactive.catalog.service.exception.PublicBucketGrantAccessException;
import org.ow2.proactive.catalog.service.model.AuthenticatedUser;
import org.ow2.proactive.catalog.util.AllBucketGrants;
import org.ow2.proactive.catalog.util.name.validator.BucketNameValidator;
import org.ow2.proactive.microservices.common.exception.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.log4j.Log4j2;


/**
 * @author ActiveEon Team
 */
@Log4j2
@RestController
@RequestMapping(value = "/buckets")
public class BucketGrantController {

    private static final String ANONYMOUS = "anonymous";

    private static final String ACTION = "[Action] ";

    public static final String PUBLIC_NO_GRANTS_ARE_ASSIGNED = " is public. No grants are assigned to it or to its objects";

    public static final String PUBLIC_CANNOT_ASSIGN_A_GRANT = " is public. You can not assign a grant to it or to any of its object";

    @Autowired
    private BucketGrantService bucketGrantService;

    @Autowired
    private GrantRightsService grantRightsService;

    @Autowired
    private RestApiAccessService restApiAccessService;

    @Value("${pa.catalog.security.required.sessionid}")
    private boolean sessionIdRequired;

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Update the access type of an existing user bucket grant")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/user", method = PUT)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public BucketGrantMetadata updateBucketGrantForAUser(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable String bucketName,
            @Parameter(description = "The name of the user that is benefiting from the access grant.", required = true) @RequestParam(value = "username", required = true, defaultValue = "") String username,
            @Parameter(description = "The new type of the access grant. It can be either noAccess, read, write or admin.", schema = @Schema(type = "string", allowableValues = { "noAccess",
                                                                                                                                                                                 "read", "write", "admin" }), required = true) @RequestParam(value = "accessType", required = true, defaultValue = "") String accessType)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata updatedUserGrant = bucketGrantService.updateBucketGrantForASpecificUser(user,
                                                                                                    bucketName,
                                                                                                    username,
                                                                                                    accessType);
        if (sessionIdRequired) {
            if (user.getName().equals(username)) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By updating this grant assigned to yourself, you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " changed grant access for user " + username + " on bucket " + bucketName +
                 " to " + accessType);
        return updatedUserGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Update the access type of an existing tenant bucket grant")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/tenant", method = PUT)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public BucketGrantMetadata updateBucketGrantForATenant(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable String bucketName,
            @Parameter(description = "The name of the tenant that is benefiting from the access grant.", required = true) @RequestParam(value = "tenant", required = true, defaultValue = "") String tenant,
            @Parameter(description = "The new type of the access grant. It can be either noAccess, read, write or admin.", schema = @Schema(type = "string", allowableValues = { "noAccess",
                                                                                                                                                                                 "read", "write", "admin" }), required = true) @RequestParam(value = "accessType", required = true, defaultValue = "") String accessType)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata updatedUserGrant = bucketGrantService.updateBucketGrantForASpecificTenant(user,
                                                                                                      bucketName,
                                                                                                      tenant,
                                                                                                      accessType);
        if (sessionIdRequired) {
            if (tenant.equals(user.getTenant())) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By updating this grant assigned to your tenant, you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " changed grant access for tenant " + tenant + " on bucket " + bucketName +
                 " to " + accessType);
        return updatedUserGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Update the access type of an existing group bucket grant")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/group", method = PUT)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public BucketGrantMetadata updateBucketGrantForAGroup(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable String bucketName,
            @Parameter(description = "The name of the group of users that are benefiting from the access grant.", required = true) @RequestParam(value = "userGroup", required = true, defaultValue = "") String userGroup,
            @Parameter(description = "The new type of the access grant. It can be either noAccess, read, write or admin.", schema = @Schema(type = "string", allowableValues = { "noAccess",
                                                                                                                                                                                 "read", "write", "admin" }), required = true) @RequestParam(value = "accessType", required = true, defaultValue = "") String accessType,
            @Parameter(description = "The new priority of the access grant.<br />It can be a value from 1 (lowest) to 10 (highest), with 5 as default." +
                                     "Priorities are used to compute the final access rights of a user belonging to multiple groups.<br />Group grants with the same priority will resolve with the default accessType order (admin > write > read > noAccess).<br />" +
                                     "Finally, please note that a user grant has always more priority than a group grant.", schema = @Schema(type = "integer", minimum = "1", maximum = "10", defaultValue = "5"), required = true) @RequestParam(value = "priority", required = true) int priority)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata updatedUserGroupGrant = bucketGrantService.updateBucketGrantForASpecificUserGroup(user,
                                                                                                              bucketName,
                                                                                                              userGroup,
                                                                                                              accessType,
                                                                                                              priority);
        if (sessionIdRequired) {
            if (user.getGroups().contains(userGroup)) {
                if (!restApiAccessService.isBucketAccessibleByUser(true, sessionId, bucketName) ||
                    !grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By updating this grant assigned to your group: " +
                                                             userGroup +
                                                             ", you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " changed grant access for group " + userGroup + " on bucket " + bucketName +
                 " to " + accessType);
        return updatedUserGroupGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Delete a user grant access for a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/user", method = DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public BucketGrantMetadata deleteBucketGrantForAUser(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName,
            @Parameter(description = "The name of the user that is benefiting from the access grant.", required = true) @RequestParam(value = "username", required = true, defaultValue = "") String username)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata deletedUSerGrant = bucketGrantService.deleteBucketGrantForAUser(bucketName, username);
        if (sessionIdRequired) {
            if (user.getName().equals(username)) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By deleting this grant assigned to yourself, you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " deleted grant access for user " + username + " on bucket " + bucketName);
        return deletedUSerGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Delete a tenant grant access for a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/tenant", method = DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public BucketGrantMetadata deleteBucketGrantForATenant(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName,
            @Parameter(description = "The name of the tenant that is benefiting from the access grant.", required = true) @RequestParam(value = "tenant", required = true, defaultValue = "") String tenant)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata deletedUSerGrant = bucketGrantService.deleteBucketGrantForATenant(bucketName, tenant);
        if (sessionIdRequired) {
            if (tenant.equals(user.getTenant())) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By deleting this grant assigned to your tenant, you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " deleted grant access for tenant " + tenant + " on bucket " + bucketName);
        return deletedUSerGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Delete a group grant access for a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/group", method = DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public BucketGrantMetadata deleteBucketGrantForAGroup(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName,
            @Parameter(description = "The name of the group of users that are benefiting from the access grant.", required = true) @RequestParam(value = "userGroup", required = true, defaultValue = "") String userGroup)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata deletedBucketGrantForUserGroup = bucketGrantService.deleteBucketGrantForAGroup(bucketName,
                                                                                                           userGroup);
        if (sessionIdRequired) {
            if (user.getGroups().contains(userGroup)) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By deleting this grant assigned to your group: " +
                                                             userGroup +
                                                             ", you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " deleted grant access for group " + userGroup + " on bucket " + bucketName);
        return deletedBucketGrantForUserGroup;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Get all grants associated with a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public List<BucketGrantMetadata> getAllGrantsForABucket(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName) {
        if (sessionIdRequired) {
            AuthenticatedUser user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
        }
        return bucketGrantService.getAllBucketGrantsForABucket(bucketName);
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Create a new user grant access for a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/user", method = POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BucketGrantMetadata createBucketGrantForAUser(
            @Parameter(description = "The the session id used to access ProActive REST server", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName,
            @Parameter(description = "The type of the access grant.<br />It can be either noAccess, read, write or admin.", schema = @Schema(type = "string", allowableValues = { "noAccess",
                                                                                                                                                                                  "read", "write", "admin" }), required = true) @RequestParam(value = "accessType", required = true) String accessType,
            @Parameter(description = "The name of the user that will benefit of the access grant.", required = true) @RequestParam(value = "username", required = true, defaultValue = "") String username)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_CANNOT_ASSIGN_A_GRANT);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata updatedUserGrant = bucketGrantService.createBucketGrantForAUser(bucketName,
                                                                                            user.getName(),
                                                                                            accessType,
                                                                                            username);
        if (sessionIdRequired) {
            if (user.getName().equals(username)) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By creating this grant for yourself, you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " created " + accessType + " grant access for user " + username + " on bucket " +
                 bucketName);
        return updatedUserGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Create a new tenant grant access for a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/tenant", method = POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BucketGrantMetadata createBucketGrantForATenant(
            @Parameter(description = "The the session id used to access ProActive REST server", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName,
            @Parameter(description = "The type of the access grant.<br />It can be either noAccess, read, write or admin.", schema = @Schema(type = "string", allowableValues = { "noAccess",
                                                                                                                                                                                  "read", "write", "admin" }), required = true) @RequestParam(value = "accessType", required = true) String accessType,
            @Parameter(description = "The name of the tenant that will benefit of the access grant.", required = true) @RequestParam(value = "tenant", required = true, defaultValue = "") String tenant)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_CANNOT_ASSIGN_A_GRANT);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata updatedUserGrant = bucketGrantService.createBucketGrantForATenant(bucketName,
                                                                                              user.getName(),
                                                                                              accessType,
                                                                                              tenant);
        if (sessionIdRequired) {
            if (tenant.equals(user.getTenant())) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By creating this grant for your tenant, you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " created " + accessType + " grant access for tenant " + tenant + " on bucket " +
                 bucketName);
        return updatedUserGrant;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Create a new user group grant access for a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/group", method = POST)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public BucketGrantMetadata createBucketGrantForAGroup(
            @Parameter(description = "The session id used to access ProActive REST server", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName,
            @Parameter(description = "The type of the access grant.<br />It can be either noAccess, read, write or admin.", schema = @Schema(type = "string", allowableValues = { "noAccess",
                                                                                                                                                                                  "read", "write", "admin" }), required = true) @RequestParam(value = "accessType", required = true) String accessType,
            @Parameter(description = "The new priority of the access grant.<br />It can be a value from 1 (lowest) to 10 (highest), with 5 as default.\n" +
                                     "Priorities are used to compute the final access rights of a user belonging to multiple groups.<br />Group grants with the same priority will resolve with the default accessType order (admin > write > read > noAccess).<br />" +
                                     "Finally, please note that a user grant has always more priority than a group grant.", schema = @Schema(type = "integer", minimum = "1", maximum = "10", defaultValue = "5"), required = true) @RequestParam(value = "priority", required = true, defaultValue = "5") int priority,
            @Parameter(description = "The name of the group of users that will benefit of the access grant.", required = true) @RequestParam(value = "userGroup", required = true, defaultValue = "") String userGroup)
            throws NotAuthenticatedException, AccessDeniedException {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_CANNOT_ASSIGN_A_GRANT);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        BucketGrantMetadata newBucketGrantForUserGroup = bucketGrantService.createBucketGrantForAGroup(bucketName,
                                                                                                       user.getName(),
                                                                                                       accessType,
                                                                                                       priority,
                                                                                                       userGroup);
        if (sessionIdRequired) {
            if (user.getGroups().contains(userGroup)) {
                if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                    throw new LostOfAdminGrantRightException("By creating this grant on your group: " + userGroup +
                                                             ", you will lose your admin rights over the bucket: " +
                                                             bucketName + ".");
                }
            }
        }
        log.info(ACTION + initiator + " created " + accessType + " grant access for group " + userGroup +
                 " on bucket " + bucketName);
        return newBucketGrantForUserGroup;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Delete all grants assigned to a bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant", method = DELETE)
    @ResponseStatus(HttpStatus.OK)
    public List<BucketGrantMetadata> deleteAllBucketGrantsAssignedToABucket(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName)
            throws NotAuthenticatedException, AccessDeniedException {
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            AuthenticatedUser user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        }
        List<BucketGrantMetadata> bucketGrantMetadataList = bucketGrantService.deleteAllGrantsAssignedToABucket(bucketName);
        log.info(ACTION + initiator + " deleted all grant accesses associated with bucket " + bucketName);
        return bucketGrantMetadataList;
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Get all grants associated with a bucket and all objects contained in this bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/all", method = GET)
    @ResponseStatus(HttpStatus.OK)
    public AllBucketGrants getAllGrantsForABucketAndItsObjects(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName) {
        if (sessionIdRequired) {
            AuthenticatedUser user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
        }
        return bucketGrantService.getAllBucketAndObjectGrants(bucketName);
    }

    @SuppressWarnings("DefaultAnnotationParam")
    @Operation(summary = "Delete all grants associated with a bucket and all objects contained in this bucket")
    @ApiResponses(value = { @ApiResponse(responseCode = "401", description = "User not authenticated"),
                            @ApiResponse(responseCode = "403", description = "Permission denied"), })
    @RequestMapping(value = "/{bucketName}/grant/all", method = DELETE)
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public AllBucketGrants deleteAllGrantsForABucketAndItsObjects(
            @Parameter(description = "The session id used to access ProActive REST server.", required = true) @RequestHeader(value = "sessionID", required = true) String sessionId,
            @Parameter(description = "The name of the bucket where the catalog objects are stored.", required = true, schema = @Schema(pattern = BucketNameValidator.VALID_BUCKET_NAME_PATTERN)) @PathVariable(value = "bucketName") String bucketName) {
        AuthenticatedUser user;
        String initiator = ANONYMOUS;
        if (sessionIdRequired) {
            user = restApiAccessService.getUserFromSessionId(sessionId);
            // Check session validation
            checkAccessAndNotPublicBucket(sessionId, user, bucketName, PUBLIC_NO_GRANTS_ARE_ASSIGNED);
            initiator = user.getName();
        } else {
            user = AuthenticatedUser.EMPTY;
        }
        AllBucketGrants deletedGrantsForTheBucketAndItsObjects = bucketGrantService.deleteAllBucketAndItsObjectsGrants(bucketName);
        if (sessionIdRequired) {
            if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
                throw new LostOfAdminGrantRightException("By deleting all grants assigned to the bucket: " +
                                                         bucketName + ", you will lose your admin rights over it");
            }
        }
        log.info(ACTION + initiator + " deleted all grant accesses associated with bucket " + bucketName +
                 " and all its objects");
        return deletedGrantsForTheBucketAndItsObjects;
    }

    private void checkAccessAndNotPublicBucket(String sessionId, AuthenticatedUser user, String bucketName,
            String publicBucketExceptionMessage) {
        // Check session validation
        if (!restApiAccessService.isUserSessionActive(sessionId, user.getName())) {
            throw new AccessDeniedException("Session id is not active. Please login.");
        }
        if (restApiAccessService.isAPublicBucket(bucketName)) {
            throw new PublicBucketGrantAccessException("Bucket: " + bucketName + publicBucketExceptionMessage);
        }
        // Check Grants
        if (!grantRightsService.getBucketRights(user, bucketName).equals(admin.toString())) {
            throw new BucketGrantAccessException(bucketName);
        }
    }
}
