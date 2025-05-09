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
package org.ow2.proactive.catalog.service.model;

import java.util.List;

import com.google.common.collect.Lists;

import lombok.Builder;
import lombok.Data;


/**
 * @author ActiveEon Team
 * @since 27/07/2017
 */
@Data
@Builder
public class AuthenticatedUser {

    public static final String ANONYMOUS = "anonymous";

    public final static AuthenticatedUser EMPTY = AuthenticatedUser.builder()
                                                                   .name(ANONYMOUS)
                                                                   .groups(Lists.newArrayList())
                                                                   .build();

    private String name;

    private List<String> groups;

    private boolean catalogAdmin = false;

    private String tenant;

    private boolean allTenantAccess = false;
}
