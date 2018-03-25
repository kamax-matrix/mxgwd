/*
 * Matrix Gateway Daemon
 * Copyright (C) 2018 Kamax Sarl
 *
 * https://www.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.matrix.gw.model.acl;

import io.kamax.matrix.gw.config.matrix.AclType;
import io.kamax.matrix.gw.config.matrix.MatrixAcl;
import io.kamax.matrix.gw.config.matrix.MatrixEndpoint;
import io.kamax.matrix.gw.model.Exchange;
import org.apache.commons.lang3.StringUtils;

public class MethodTargetHandler implements AclTargetHandler {

    public boolean isAllowed(Exchange ex, MatrixEndpoint endpoint, MatrixAcl acl) {
        boolean isMethod = StringUtils.equals(acl.getValue(), ex.getRequest().getMethod());

        if (AclType.Blacklist.is(acl) && isMethod)
            return false;

        if (AclType.Whitelist.is(acl) && !isMethod)
            return false;

        return true;
    }

}
