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

package io.kamax.mxgwd.undertow.matrix.client;

import io.kamax.mxgwd.model.Gateway;
import io.kamax.mxgwd.model.Request;
import io.kamax.mxgwd.model.Response;
import io.kamax.mxgwd.undertow.HttpServerExchangeHandler;
import io.undertow.server.HttpServerExchange;

public class ActivePoliciesListingHandler extends HttpServerExchangeHandler {

    private Gateway gw;

    public ActivePoliciesListingHandler(Gateway gw) {
        this.gw = gw;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        Request req = extract(exchange);
        Response response = gw.getEffectivePolicies(req);
        sendResponse(exchange, response);
    }

}
