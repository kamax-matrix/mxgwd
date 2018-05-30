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

package io.kamax.mxgwd.config;

import io.kamax.mxgwd.config.admin.Admin;
import io.kamax.mxgwd.config.matrix.Matrix;
import io.kamax.mxgwd.config.server.Server;

public class Config {

    private Matrix matrix;
    private Server server;
    private Admin admin;
    private Storage storage;

    public Matrix getMatrix() {
        return Value.get(matrix, Matrix::new);
    }

    public void setMatrix(Matrix matrix) {
        this.matrix = matrix;
    }

    public Server getServer() {
        return Value.get(server, Server::new);
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Admin getAdmin() {
        return Value.get(admin, Admin::new);
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Storage getStorage() {
        return Value.get(storage, Storage::new);
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

}
