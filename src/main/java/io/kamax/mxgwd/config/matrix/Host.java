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

package io.kamax.mxgwd.config.matrix;

import io.kamax.mxgwd.config.Value;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Host {

    private URL to;
    private URL toIdentity;
    private List<Endpoint> endpoints;
    private List<Long> entities = new ArrayList<>();

    public URL getTo() {
        return to;
    }

    public void setTo(URL to) {
        this.to = to;
    }

    public URL getToIdentity() {
        return toIdentity;
    }

    public void setToIdentity(URL toIdentity) {
        this.toIdentity = toIdentity;
    }

    public List<Endpoint> getEndpoints() {
        return Value.get(endpoints, ArrayList::new);
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public List<Long> getEntities() {
        return entities;
    }

    public void setEntities(List<Long> entities) {
        this.entities = entities;
    }

    public void addEntity(Long id) {
        entities.add(id);
    }

}
