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

package io.kamax.mxgwd.undertow;

import io.kamax.matrix.json.GsonUtil;
import io.kamax.mxgwd.model.Request;
import io.kamax.mxgwd.model.Response;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class HttpServerExchangeHandler implements HttpHandler {

    protected Request extract(HttpServerExchange exchange) throws MalformedURLException {
        Request reqOut = new Request();

        reqOut.setMethod(exchange.getRequestMethod().toString());
        reqOut.setUrl(new URL(exchange.getRequestURL()));

        Map<String, List<String>> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach(h -> {
            headers.put(h.getHeaderName().toString(), Arrays.asList(h.toArray()));
        });
        reqOut.setHeaders(headers);

        Map<String, List<String>> parameters = new HashMap<>();
        exchange.getQueryParameters().forEach((k, v) -> {
            parameters.put(k, new ArrayList<>(v));
        });
        reqOut.setQuery(parameters);

        return reqOut;
    }

    protected void sendResponse(HttpServerExchange exchange, Response response) {
        try {
            exchange.setStatusCode(response.getStatus());
            response.getHeaders().forEach((k, v) -> {
                v.forEach(vv -> exchange.getResponseHeaders().add(HttpString.tryFromString(k), vv));
            });
            response.getBody().ifPresent(body -> {
                exchange.setResponseContentLength(body.length);
                exchange.getResponseSender().send(ByteBuffer.wrap(body));
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected void sendJsonResponse(HttpServerExchange exchange, String body) {
        exchange.setStatusCode(200);
        exchange.getResponseHeaders().add(HttpString.tryFromString("Content-Type"), "application/json");
        exchange.setResponseContentLength(body.length());
        exchange.getResponseSender().send(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
    }

    protected void sendJsonResponse(HttpServerExchange exchange, Object o) {
        sendJsonResponse(exchange, GsonUtil.get().toJson(o));
    }

}
