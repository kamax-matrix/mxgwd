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

import io.kamax.matrix.gw.config.matrix.MatrixEndpoint;
import io.kamax.matrix.gw.config.matrix.MatrixHost;

import java.util.Optional;

public class Exchange {

    private Request request;
    private Response response;

    private Context context;
    private String hostname;
    private MatrixHost host;
    private MatrixEndpoint endpoint;

    public Exchange(Request request, Context context, String hostname, MatrixHost host) {
        this.request = request;
        this.context = context;
        this.hostname = hostname;
        this.host = host;
    }

    public Request getRequest() {
        return request;
    }

    public Context getContext() {
        return context;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public String getHostname() {
        return hostname;
    }

    public MatrixHost getHost() {
        return host;
    }

    public Optional<MatrixEndpoint> getEndpoint() {
        return Optional.ofNullable(endpoint);
    }

    public void setEndpoint(MatrixEndpoint endpoint) {
        this.endpoint = endpoint;
    }

}
