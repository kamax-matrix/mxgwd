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

import io.kamax.mxgwd.config.matrix.Acl;
import io.kamax.mxgwd.config.matrix.AclType;
import io.kamax.mxgwd.config.matrix.Endpoint;
import io.kamax.mxgwd.model.Exchange;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class UserTargetHandler implements AclTargetHandler {

    public boolean isMatch(Exchange ex, Acl acl) {
        if (Objects.isNull(ex.getContext().getUser())) {
            return false;
        }

        return StringUtils.equals(acl.getValue(), ex.getContext().getUser().getId());
    }

    public boolean isAllowed(Exchange ex, Endpoint endpoint, Acl acl) {
        boolean isMatch = isMatch(ex, acl);

        if (AclType.Blacklist.is(acl) && isMatch)
            return false;

        if (AclType.Whitelist.is(acl) && !isMatch)
            return false;

        return true;
    }

}
