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

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MatrixGateway {

    private CloseableHttpClient client = HttpClients.custom().setUserAgent("mxgwd/0.0.0").build();

    public Response execute(Request request) throws URISyntaxException, IOException {
        URIBuilder b = new URIBuilder(request.getUrl().toString());
        b.setPort(8008);
        request.getQuery().forEach((name, values) -> values.forEach(value -> b.addParameter(name, value)));
        switch (request.getMethod()) {
            case "GET":
                HttpGet get = new HttpGet(b.build());
                request.getHeaders().forEach((name, values) -> values.forEach(value -> get.addHeader(name, value)));
                try (CloseableHttpResponse resIn = client.execute(get)) {
                    Map<String, List<String>> headers = new HashMap<>();
                    for (Header header : resIn.getAllHeaders()) {
                        String name = header.getName();
                        String value = header.getValue();
                        if (!headers.containsKey(name)) {
                            headers.put(name, new ArrayList<>());
                        }
                        headers.get(name).add(value);
                    }
                    Response resOut = new Response();
                    resOut.setStatus(resIn.getStatusLine().getStatusCode());
                    resOut.setHeaders(headers);
                    resOut.setBody(EntityUtils.toString(resIn.getEntity()));
                    return resOut;
                }
            default:
                throw new RuntimeException("Unsupported method: " + request.getMethod());

        }
    }

}
