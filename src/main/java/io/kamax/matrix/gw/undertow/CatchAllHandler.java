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

package io.kamax.matrix.gw.undertow;

import io.kamax.matrix.gw.model.Gateway;
import io.kamax.matrix.gw.model.Request;
import io.kamax.matrix.gw.model.Response;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;

public class CatchAllHandler implements HttpHandler {

    private Gateway gw;

    public CatchAllHandler(Gateway gw) {
        this.gw = gw;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        try {
            URL url = new URL(exchange.getRequestURL());
            Map<String, List<String>> headers = new HashMap<>();
            exchange.getRequestHeaders().forEach(h -> {
                headers.put(h.getHeaderName().toString(), Arrays.asList(h.toArray()));
            });

            Map<String, List<String>> parameters = new HashMap<>();
            exchange.getQueryParameters().forEach((k, v) -> {
                parameters.put(k, new ArrayList<>(v));
            });

            exchange.getRequestReceiver().receiveFullBytes((exchange1, message) -> {
                Request reqOut = new Request();
                reqOut.setMethod(exchange1.getRequestMethod().toString());
                reqOut.setUrl(url);
                reqOut.setHeaders(headers);
                reqOut.setQuery(parameters);
                if (message.length > 0) {
                    reqOut.setBody(message);
                }

                try {
                    Response resIn = gw.execute(reqOut);
                    exchange1.setStatusCode(resIn.getStatus());
                    resIn.getHeaders().forEach((k, v) -> {
                        v.forEach(vv -> exchange1.getResponseHeaders().add(HttpString.tryFromString(k), vv));
                    });
                    resIn.getBody().ifPresent(body -> {
                        exchange1.setResponseContentLength(body.length);
                        exchange1.getResponseSender().send(ByteBuffer.wrap(body));
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            exchange.getResponseHeaders().put(HttpString.tryFromString("Server"), "mxgwd");
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("Internal Server Error");
        } finally {
            exchange.endExchange();
        }
    }

}
