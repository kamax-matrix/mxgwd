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

import io.kamax.matrix.gw.config.Config;
import io.kamax.matrix.gw.config.matrix.AclType;
import io.kamax.matrix.gw.config.matrix.MatrixAcl;
import io.kamax.matrix.gw.config.matrix.MatrixEndpoint;
import io.kamax.matrix.gw.config.matrix.MatrixHost;
import io.kamax.matrix.json.GsonUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class Gateway {

    private final Logger log = LoggerFactory.getLogger(Gateway.class);

    private Config cfg;
    private CloseableHttpClient client;

    public Gateway(Config cfg) {
        this.cfg = cfg;
        log.info("Config: {}", GsonUtil.getPrettyForLog(cfg));
        client = HttpClients.custom()
                .setUserAgent("mxgwd/0.0.0") //FIXME use git
                .build();
    }

    private Response execute(Request reqIn, HttpRequestBase reqOut) throws IOException {
        reqIn.getHeaders().forEach((name, values) -> values.forEach(value -> {
            if (StringUtils.equals(name, "Host")) {
                return;
            }

            if (StringUtils.equals(name, "Content-Length")) {
                return;
            }

            reqOut.addHeader(name, value);
        }));
        try (CloseableHttpResponse resIn = client.execute(reqOut)) {
            Map<String, List<String>> headers = new HashMap<>();
            for (Header header : resIn.getAllHeaders()) {
                if (header.getName().equalsIgnoreCase("Transfer-Encoding")) {
                    continue;
                }

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
            resOut.setBody(IOUtils.toByteArray(resIn.getEntity().getContent()));
            return resOut;
        }
    }

    private boolean isAllowed(Request request, String hostname, MatrixHost mxHost) {
        for (MatrixEndpoint endpoint : mxHost.getEndpoints()) {
            boolean pathMatch = StringUtils.startsWith(request.getUrl().getPath(), endpoint.getPath());
            boolean methodBlank = StringUtils.isBlank(endpoint.getMethod());
            boolean methodMatch = StringUtils.equals(endpoint.getMethod(), request.getMethod());

            if (!pathMatch || (!methodBlank && !methodMatch)) {
                continue;
            }

            for (MatrixAcl acl : endpoint.getAcls()) {
                if (StringUtils.equals("method", acl.getTarget())) {
                    boolean isMethod = StringUtils.equals(acl.getValue(), request.getMethod());

                    if (AclType.Blacklist.is(acl) && isMethod)
                        return false;

                    if (AclType.Whitelist.is(acl) && !isMethod)
                        return false;
                } else {
                    throw new RuntimeException("Unsupported ACL target " + acl.getTarget() + " for " + hostname + endpoint.getPath());
                }
            }
        }

        return Objects.nonNull(mxHost.getTo());
    }

    public Response execute(Request request) throws URISyntaxException, IOException {
        URL url = request.getUrl();
        String host = url.getHost() + (url.getPort() != -1 ? ":" + url.getPort() : "");
        URIBuilder b = new URIBuilder(request.getUrl().toString());

        MatrixHost mxHost = Optional.ofNullable(cfg.getMatrix().getClient().getHosts().get(host)).orElseThrow(RuntimeException::new);
        b.setScheme(mxHost.getTo().getProtocol());
        b.setHost(mxHost.getTo().getHost());
        if (mxHost.getTo().getPort() != -1) {
            b.setPort(mxHost.getTo().getPort());
        }

        if (!isAllowed(request, host, mxHost)) {
            log.info("Reject {} {}", request.getMethod(), request.getUrl());
            return Response.rejectByPolicy();
        }
        log.info("Allow {} {} to {} {}", request.getMethod(), request.getUrl(), request.getMethod(), b);

        request.getQuery().forEach((name, values) -> values.forEach(value -> b.addParameter(name, value)));
        URI uri = b.build();

        HttpRequestBase proxyRequest = null;
        // FIXME map of handler?
        switch (request.getMethod()) {
            case "OPTIONS":
                proxyRequest = new HttpOptions(uri);
                break;
            case "HEAD":
                proxyRequest = new HttpHead(uri);
                break;
            case "GET":
                proxyRequest = new HttpGet(uri);
                break;
            case "DELETE":
                proxyRequest = new HttpDelete(uri);
                break;
            case "PATCH":
                HttpPatch patchReq = new HttpPatch(uri);
                patchReq.setEntity(new ByteArrayEntity(request.getBody()));
                proxyRequest = patchReq;
                break;
            case "POST":
                HttpPost postReq = new HttpPost(uri);
                postReq.setEntity(new ByteArrayEntity(request.getBody()));
                proxyRequest = postReq;
                break;
            case "PUT":
                HttpPut putReq = new HttpPut(uri);
                putReq.setEntity(new ByteArrayEntity(request.getBody()));
                proxyRequest = putReq;
                break;
        }

        if (Objects.isNull(proxyRequest)) {
            throw new RuntimeException("Unsupported method: " + request.getMethod());
        }

        return execute(request, proxyRequest);
    }

}
