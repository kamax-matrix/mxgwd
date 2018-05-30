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

import com.google.gson.JsonObject;
import io.kamax.matrix.json.GsonUtil;
import io.kamax.mxgwd.model.Gateway;
import io.kamax.mxgwd.model.Request;
import io.kamax.mxgwd.undertow.HttpServerExchangeHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

public class CatchAllHandler extends HttpServerExchangeHandler {

    private Gateway gw;

    public CatchAllHandler(Gateway gw) {
        this.gw = gw;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        try {
            exchange.getRequestReceiver().receiveFullBytes((exchange1, message) -> {
                try {
                    Request reqOut = extract(exchange);
                    if (message.length > 0) {
                        reqOut.setBody(message);
                    }
                    sendResponse(exchange1, gw.execute(reqOut));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();

            JsonObject body = new JsonObject();
            body.addProperty("errcode", "M_UNKNOWN");
            body.addProperty("error", "Internal Server Error");
            body.addProperty("error_internal", e.getMessage());
            exchange.getResponseHeaders().put(HttpString.tryFromString("Server"), "mxgwd");
            exchange.setStatusCode(500);
            exchange.getResponseSender().send(GsonUtil.get().toJson(body));
        } finally {
            exchange.endExchange();
        }
    }

}
