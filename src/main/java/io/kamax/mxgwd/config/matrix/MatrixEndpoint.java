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

public class MatrixEndpoint {

    private String method;
    private String path;
    private String match;
    private String action;
    private URL to;
    private List<MatrixAcl> acls;

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMatch() {
        return match;
    }

    public void setMatch(String match) {
        this.match = match;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public URL getTo() {
        return to;
    }

    public void setTo(URL to) {
        this.to = to;
    }

    public List<MatrixAcl> getAcls() {
        return Value.get(acls, ArrayList::new);
    }

    public void setAcls(List<MatrixAcl> acls) {
        this.acls = acls;
    }

}
