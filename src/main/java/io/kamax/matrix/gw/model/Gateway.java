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
import io.kamax.matrix.gw.config.matrix.MatrixAcl;
import io.kamax.matrix.gw.config.matrix.MatrixEndpoint;
import io.kamax.matrix.gw.config.matrix.MatrixHost;
import io.kamax.matrix.gw.model.acl.AclTargetHandler;
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
import java.util.stream.Stream;

public class Gateway {

    private final Logger log = LoggerFactory.getLogger(Gateway.class);

    private Config cfg;

    private ActionMapper actionMapper;
    private AclTargetHandlerMapper aclTargetMapper;

    private CloseableHttpClient client;

    public Gateway(Config cfg) {
        this.cfg = cfg;

        actionMapper = new ActionMapper(); // TODO make configurable
        aclTargetMapper = new AclTargetHandlerMapper(); // TODO make configurable
        client = HttpClients.custom()
                .setUserAgent("mxgwd/0.0.0") // FIXME use git
                .build();

        processConfig();
    }

    private void processConfig() {
        log.info("Config: Processing");
        for (String hostName : cfg.getMatrix().getClient().getHosts().keySet()) {
            MatrixHost host = cfg.getMatrix().getClient().getHosts().get(hostName);
            log.info("Host {}: Processing", hostName);
            for (MatrixEndpoint endpoint : host.getEndpoints()) {
                for (MatrixAcl acl : endpoint.getAcls()) {
                    if (!aclTargetMapper.map(acl.getTarget()).isPresent()) {
                        throw new RuntimeException("Unknown ACL target type: " + acl.getTarget());
                    }
                }

                if (StringUtils.isNotBlank(endpoint.getAction())) {
                    MethodPath mp = actionMapper.map(endpoint.getAction()).orElseThrow(() -> new RuntimeException("Unknown action " + endpoint.getAction()));
                    log.info("Endpoint: {} to {}:{}", endpoint.getAction(), mp.getMethod(), mp.getPath());

                    endpoint.setMethod(mp.getMethod());
                    endpoint.setPath(mp.getPath());
                }
            }
        }
    }

    private AclTargetHandler getAclTargetHandler(String id) {
        return aclTargetMapper.map(id).orElseThrow(() -> new RuntimeException("Unknown ACL target type: " + id));
    }

    private Optional<MatrixHost> findHostFor(URL url) {
        return Optional.ofNullable(cfg.getMatrix().getClient().getHosts().get(url.getAuthority()));
    }

    private Optional<String> findAccessTokenInHeaders(Request request) {
        return request.getHeaders().entrySet().stream()
                .filter(e -> StringUtils.equals("Authorization", e.getKey()))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .filter(v -> StringUtils.startsWith("Bearer ", v))
                .map(v -> v.substring("Bearer ".length()))
                .findAny();
    }

    private Optional<String> findAccessTokenInQuery(Request request) {
        return request.getQuery().entrySet().stream()
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .findAny();
    }

    private Optional<String> findAccessToken(Request request) {
        return Stream.of(findAccessTokenInHeaders(request), findAccessTokenInQuery(request)) // FIXME do lazy loading
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
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
                if (!getAclTargetHandler(acl.getTarget()).isAllowed(
                        request,
                        hostname,
                        mxHost,
                        endpoint,
                        acl
                )) {
                    return false;
                }
            }
        }

        return Objects.nonNull(mxHost.getTo());
    }

    public Response execute(Request request) throws URISyntaxException, IOException {
        // We find if the host has been configured (and so, allowed)
        MatrixHost mxHost = findHostFor(request.getUrl()).orElseThrow(RuntimeException::new);

        // We build the security context; finding out if the caller is authenticated, its identity, the roles it has
        Context context = new Context();
        findAccessToken(request).ifPresent(context::setAccessToken);

        // We build the exchange object, bundling all the data so far
        Exchange ex = new Exchange(request, new Context(), request.getUrl().getAuthority(), mxHost);

        if (!isAllowed(request, request.getRoot(), mxHost)) {
            log.info("Reject {}:{}", request.getMethod(), request.getUrl());
            return Response.rejectByPolicy();
        }
        log.info("Allow {}:{}", request.getMethod(), request.getUrl().toString());

        URIBuilder b = new URIBuilder(request.getUrl().toString());
        b.setScheme(mxHost.getTo().getProtocol());
        b.setHost(mxHost.getTo().getHost());
        if (mxHost.getTo().getPort() != -1) {
            b.setPort(mxHost.getTo().getPort());
        }
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
