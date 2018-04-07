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

package io.kamax.mxgwd.model;

import com.google.gson.JsonObject;
import io.kamax.matrix.MatrixID;
import io.kamax.matrix.json.GsonUtil;
import io.kamax.mxgwd.config.Config;
import io.kamax.mxgwd.config.matrix.MatrixAcl;
import io.kamax.mxgwd.config.matrix.MatrixEndpoint;
import io.kamax.mxgwd.config.matrix.MatrixHost;
import io.kamax.mxgwd.model.acl.AclTargetHandler;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
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
                .setMaxConnPerRoute(Integer.MAX_VALUE) // TODO make configurable
                .setMaxConnTotal(Integer.MAX_VALUE) // TODO make configurable
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
                    MethodPath mp = actionMapper.map(endpoint.getAction())
                            .orElseThrow(() -> new RuntimeException("Unknown action " + endpoint.getAction()));
                    log.info("Endpoint: {} to {}:{}", endpoint.getAction(), mp.getMethod(), mp.getPath());

                    endpoint.setMethod(mp.getMethod());
                    endpoint.setPath(mp.getPath());
                }

                if (Objects.isNull(host.getTo()) && Objects.isNull(endpoint.getTo())) {
                    throw new RuntimeException("A backend URL must be provider either at the host or at the endpoint level");
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

    private MatrixHost getHostFor(URL url) {
        return findHostFor(url).orElseThrow(RuntimeException::new);
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

    private URI buildUri(URL root, String path) {
        try {
            return new URIBuilder(root.toURI()).setPath(path).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String urlEncode(String v) {
        try {
            return URLEncoder.encode(v, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private Response execute(Request reqIn, HttpRequestBase reqOut) throws IOException {
        reqIn.getHeaders().forEach((name, values) -> values.forEach(value -> {
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

    private boolean isAllowed(Exchange ex) {
        return ex.getEndpoint().map(endpoint -> {
            for (MatrixAcl acl : endpoint.getAcls()) {
                if (!getAclTargetHandler(acl.getTarget()).isAllowed(
                        ex,
                        endpoint,
                        acl
                )) {
                    return false;
                }
            }

            return true;
        }).orElse(Objects.nonNull(ex.getHost().getTo()));
    }

    private Exchange buildExchange(Request request, MatrixHost mxHost) {
        Context context = new Context();

        // We build the security context; finding out if the caller is authenticated, its identity, the roles it has
        findAccessToken(request).ifPresent(token -> {
            context.setAccessToken(token);

            // We discover who we are
            HttpGet whoamiReq = new HttpGet(buildUri(mxHost.getTo(), "/_matrix/client/r0/account/whoami"));
            whoamiReq.addHeader("Authorization", "Bearer " + token);
            try (CloseableHttpResponse whoamiRes = client.execute(whoamiReq)) {
                context.setAuthenticated(whoamiRes.getStatusLine().getStatusCode() == 200);
                if (!context.isAuthenticated()) {
                    log.info("Access token is not valid");
                    return;
                }

                String body = EntityUtils.toString(whoamiRes.getEntity());
                String uId = GsonUtil.getStringOrThrow(GsonUtil.parseObj(body), "user_id");
                log.info("User: {}", uId);
                context.setUser(MatrixID.asAcceptable(uId));

                if (Objects.nonNull(mxHost.getToIdentity())) {
                    HttpGet groupsReq = new HttpGet(buildUri(mxHost.getToIdentity(), "/_matrix-internal/profile/v1/" + urlEncode(uId)));
                    try (CloseableHttpResponse groupsRes = client.execute(groupsReq)) {
                        if (groupsRes.getStatusLine().getStatusCode() != 200) {
                            throw new RuntimeException("Unable to fetch user's data");
                        }

                        JsonObject groupsBody = GsonUtil.parseObj(EntityUtils.toString(groupsRes.getEntity()));
                        GsonUtil.findArray(groupsBody, "roles")
                                .ifPresent(arr -> context.setRoles(GsonUtil.asList(arr, String.class)));
                        log.info("Roles: {}", GsonUtil.get().toJson(context.getRoles().orElse(Collections.emptyList())));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Unable to fetch user's data", e);
            }
        });

        // We build the exchange object, bundling all the data so far
        Exchange ex = new Exchange(request, context, request.getUrl().getAuthority(), mxHost);

        // We try to find a matching endpoint
        for (MatrixEndpoint endpoint : mxHost.getEndpoints()) {
            boolean pathMatch;
            if ("regexp".equals(endpoint.getMatch())) {
                pathMatch = Pattern.compile(endpoint.getPath()).matcher(request.getUrl().getPath()).matches();
            } else {
                pathMatch = StringUtils.startsWith(request.getUrl().getPath(), endpoint.getPath());
            }

            boolean methodBlank = StringUtils.isBlank(endpoint.getMethod());
            boolean methodMatch = methodBlank || StringUtils.equals(endpoint.getMethod(), request.getMethod());

            if (!pathMatch || !methodMatch) {
                continue;
            }

            ex.setEndpoint(endpoint);
            break;
        }

        return ex;
    }

    private Exchange buildExchange(Request request) {
        return buildExchange(request, getHostFor(request.getUrl()));
    }

    private Response proxyRequest(Exchange ex) throws URISyntaxException, IOException {
        URIBuilder b = new URIBuilder(ex.getRequest().getUrl().toString());
        URL targetHost = ex.getEndpoint().map(MatrixEndpoint::getTo).orElse(ex.getHost().getTo());
        b.setScheme(targetHost.getProtocol());
        b.setHost(targetHost.getHost());
        if (targetHost.getPort() != -1) {
            b.setPort(targetHost.getPort());
        }
        URL targetEndpoint = b.build().toURL();
        ex.getRequest().getQuery().forEach((name, values) -> values.forEach(value -> b.addParameter(name, value)));
        URI uri = b.build();

        HttpRequestBase proxyRequest = null;
        // FIXME map of handler?
        switch (ex.getRequest().getMethod()) {
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
                patchReq.setEntity(new ByteArrayEntity(ex.getRequest().getBody()));
                proxyRequest = patchReq;
                break;
            case "POST":
                HttpPost postReq = new HttpPost(uri);
                postReq.setEntity(new ByteArrayEntity(ex.getRequest().getBody()));
                proxyRequest = postReq;
                break;
            case "PUT":
                HttpPut putReq = new HttpPut(uri);
                putReq.setEntity(new ByteArrayEntity(ex.getRequest().getBody()));
                proxyRequest = putReq;
                break;
        }

        if (Objects.isNull(proxyRequest)) {
            throw new RuntimeException("Unsupported method: " + ex.getRequest().getMethod());
        }

        log.info("Fetch {}:{}", ex.getRequest().getMethod(), targetEndpoint.toString());
        Response response = execute(ex.getRequest(), proxyRequest);
        ex.setResponse(response);
        log.info("Done {}:{}", ex.getRequest().getMethod(), ex.getRequest().getUrl());

        return response;
    }

    public Response getEffectivePolicies(Request request) {
        Exchange ex = buildExchange(request);

        Map<String, Boolean> policies = new HashMap<>();
        for (MatrixEndpoint endpoint : ex.getHost().getEndpoints()) {
            actionMapper.map(endpoint.getAction()).ifPresent(mp -> {
                boolean allowed = endpoint.getAcls().stream()
                        .allMatch(acl -> getAclTargetHandler(acl.getTarget()).isAllowed(ex, endpoint, acl));
                policies.put(endpoint.getAction(), allowed);
            });
        }

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        Response response = new Response();
        response.setStatus(200);
        response.setHeaders(headers);
        response.setBody(GsonUtil.get().toJson(policies).getBytes());
        return response;
    }

    public Response execute(Request request) throws URISyntaxException, IOException {
        log.info("Handle {}:{}", request.getMethod(), request.getUrl());

        Exchange ex = buildExchange(request);
        if (!isAllowed(ex)) {
            log.info("Reject {}:{}", request.getMethod(), request.getUrl());
            return Response.rejectByPolicy();
        }
        log.info("Allow {}:{}", request.getMethod(), request.getUrl().toString());

        return proxyRequest(ex);
    }

}
