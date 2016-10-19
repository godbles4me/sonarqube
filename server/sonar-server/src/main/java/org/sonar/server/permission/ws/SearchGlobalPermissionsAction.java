/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.permission.ws;

import java.util.Locale;
import java.util.Optional;
import org.sonar.api.i18n.I18n;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.permission.PermissionQuery;
import org.sonar.server.user.UserSession;
import org.sonarqube.ws.WsPermissions.Permission;
import org.sonarqube.ws.WsPermissions.WsSearchGlobalPermissionsResponse;

import static org.sonar.server.permission.PermissionPrivilegeChecker.checkProjectAdmin;
import static org.sonar.server.permission.ws.PermissionsWsParametersBuilder.createOrganizationParameter;
import static org.sonar.server.ws.WsUtils.writeProtobuf;
import static org.sonarqube.ws.WsPermissions.Permission.newBuilder;
import static org.sonarqube.ws.client.permission.PermissionsWsParameters.PARAM_ORGANIZATION_KEY;

public class SearchGlobalPermissionsAction implements PermissionsWsAction {

  public static final String ACTION = "search_global_permissions";
  private static final String PROPERTY_PREFIX = "global_permissions.";
  private static final String DESCRIPTION_SUFFIX = ".desc";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final I18n i18n;
  private final PermissionWsSupport support;

  public SearchGlobalPermissionsAction(DbClient dbClient, UserSession userSession, I18n i18n, PermissionWsSupport support) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.i18n = i18n;
    this.support = support;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION)
      .setDescription("List global permissions. <br />" +
        "It requires administration permissions to access.")
      .setResponseExample(getClass().getResource("search_global_permissions-example.json"))
      .setSince("5.2")
      .setHandler(this);

    createOrganizationParameter(action);
  }

  @Override
  public void handle(Request wsRequest, Response wsResponse) throws Exception {
    try (DbSession dbSession = dbClient.openSession(false)) {
      OrganizationDto org = support.findOrganization(dbSession, wsRequest.param(PARAM_ORGANIZATION_KEY));
      checkProjectAdmin(userSession, org.getUuid(), Optional.empty());

      WsSearchGlobalPermissionsResponse response = buildResponse(dbSession, org);
      writeProtobuf(response, wsRequest, wsResponse);
    }
  }

  private WsSearchGlobalPermissionsResponse buildResponse(DbSession dbSession, OrganizationDto org) {
    WsSearchGlobalPermissionsResponse.Builder response = WsSearchGlobalPermissionsResponse.newBuilder();
    Permission.Builder permission = newBuilder();

    for (String permissionKey : GlobalPermissions.ALL) {
      PermissionQuery permissionQuery = permissionQuery(permissionKey);

      response.addPermissions(
        permission
          .clear()
          .setKey(permissionKey)
          .setName(i18nName(permissionKey))
          .setDescription(i18nDescriptionMessage(permissionKey))
          .setUsersCount(countUsers(dbSession, org, permissionQuery))
          .setGroupsCount(countGroups(dbSession, org, permissionKey)));
    }

    return response.build();
  }

  private String i18nDescriptionMessage(String permissionKey) {
    return i18n.message(Locale.ENGLISH, PROPERTY_PREFIX + permissionKey + DESCRIPTION_SUFFIX, "");
  }

  private String i18nName(String permissionKey) {
    return i18n.message(Locale.ENGLISH, PROPERTY_PREFIX + permissionKey, permissionKey);
  }

  private int countGroups(DbSession dbSession, OrganizationDto org, String permission) {
    PermissionQuery query = PermissionQuery.builder().setPermission(permission).build();
    return dbClient.groupPermissionDao().countGroupsByQuery(dbSession, org.getUuid(), query);
  }

  private int countUsers(DbSession dbSession, OrganizationDto org, PermissionQuery permissionQuery) {
    return dbClient.userPermissionDao().countUsers(dbSession, org.getUuid(), permissionQuery);
  }

  private static PermissionQuery permissionQuery(String permissionKey) {
    return PermissionQuery.builder()
      .setPermission(permissionKey)
      .withAtLeastOnePermission()
      .build();
  }
}
