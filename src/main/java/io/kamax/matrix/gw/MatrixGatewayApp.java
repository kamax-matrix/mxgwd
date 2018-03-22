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

package io.kamax.matrix.gw;

import io.kamax.matrix.gw.model.MatrixGateway;
import io.kamax.matrix.gw.model.Request;
import io.kamax.matrix.gw.model.Response;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.util.HttpString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

public class MatrixGatewayApp {

    private final static Logger log = LoggerFactory.getLogger(MatrixGatewayApp.class);

    public static void main(String[] args) {
        MatrixGateway gw = new MatrixGateway();
        int httpPort = Optional.ofNullable(System.getenv("UNDERTOW_HTTP_PORT"))
                .map(Integer::parseInt)
                .orElse(8009);

        Undertow server = Undertow.builder()
                .addHttpListener(httpPort, "0.0.0.0")
                .setHandler(Handlers.path()
                        .addPrefixPath("/", exchange -> {
                            Map<String, List<String>> headers = new HashMap<>();
                            exchange.getRequestHeaders().forEach(h -> {
                                headers.put(h.getHeaderName().toString(), Arrays.asList(h.toArray()));
                            });

                            Request reqOut = new Request();
                            reqOut.setMethod(exchange.getRequestMethod().toString());
                            reqOut.setUrl(new URL(exchange.getRequestURL()));
                            reqOut.setHeaders(headers);
                            //reqOut.setQuery(parameters);

                            Response resIn = gw.execute(reqOut);
                            exchange.setStatusCode(resIn.getStatus());
                            resIn.getHeaders().forEach((k, v) -> v.forEach(vv -> exchange.getResponseHeaders().add(new HttpString(k), vv)));
                            resIn.getBody().ifPresent(body -> {
                                exchange.setResponseContentLength(body.length());
                                exchange.getResponseSender().send(body);
                                exchange.endExchange();
                            });
                        })
                ).build();

        server.start();
    }

}
