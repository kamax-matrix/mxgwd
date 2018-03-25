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

package io.kamax.matrix.gw.model;

import io.kamax.matrix._MatrixID;

import java.util.List;
import java.util.Optional;

public class Context {

    private boolean authenticated;
    private String accessToken;
    private _MatrixID user;
    private List<String> roles;

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public _MatrixID getUser() {
        return user;
    }

    public void setUser(_MatrixID user) {
        this.user = user;
    }

    public Optional<List<String>> getRoles() {
        return Optional.ofNullable(roles);
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

}
