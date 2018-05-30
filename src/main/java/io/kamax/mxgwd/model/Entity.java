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

import io.kamax.mxgwd.config.matrix.EntityIO;
import io.kamax.mxgwd.storage.Store;

public class Entity {

    private Store store;
    private EntityIO io;

    public Entity(Store store, EntityIO io) {
        this.store = store;
        this.io = io;
    }

    public long getId() {
        return io.getId();
    }

    public String getHost() {
        return io.getHost();
    }

    public String getType() {
        return io.getType();
    }

    public String getName() {
        return io.getName();
    }

    public String getAclType() {
        return io.getAclType();
    }

    public void setAclType(String type) {
        io.setAclType(type);
        store.updateEntity(io);
    }

    public String getAclTarget() {
        return io.getAclTarget();
    }

    public String getAclValue() {
        return io.getAclValue();
    }

}
