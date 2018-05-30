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

package io.kamax.mxgwd.model;

import io.kamax.mxgwd.config.matrix.Acl;
import io.kamax.mxgwd.config.matrix.Endpoint;
import io.kamax.mxgwd.config.matrix.EntityIO;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EntityMapper {

    private Map<String, Function<EntityIO, List<Endpoint>>> map = new HashMap<>();

    public EntityMapper() {
        map.put("m.room", entity -> {
            Endpoint ep = new Endpoint();
            ep.setMatch("regexp");
            ep.setPath("/_matrix/client/r0/rooms/" + entity.getName() + "/(.*)?");
            Acl acl = new Acl();
            acl.setType(entity.getAclType());
            acl.setTarget(entity.getAclTarget());
            acl.setValue(entity.getAclValue());
            ep.setAcls(Collections.singletonList(acl));
            return Collections.singletonList(ep);
        });
    }

    public List<Endpoint> map(EntityIO e) {
        return map.getOrDefault(e.getType(), e1 -> Collections.emptyList()).apply(e);
    }

}
