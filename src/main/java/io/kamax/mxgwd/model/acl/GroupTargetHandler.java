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

package io.kamax.mxgwd.model.acl;

import io.kamax.mxgwd.config.matrix.AclType;
import io.kamax.mxgwd.config.matrix.MatrixAcl;
import io.kamax.mxgwd.config.matrix.MatrixEndpoint;
import io.kamax.mxgwd.model.Exchange;

public class GroupTargetHandler implements AclTargetHandler {

    @Override
    public boolean isAllowed(Exchange ex, MatrixEndpoint endpoint, MatrixAcl acl) {
        return ex.getContext().getRoles().map(list -> {
            if (AclType.Blacklist.is(acl) && list.contains(acl.getValue()))
                return false;

            if (AclType.Whitelist.is(acl) && !list.contains(acl.getValue()))
                return false;

            return true;
        }).orElse(false);
    }

}
