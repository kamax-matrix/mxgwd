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
import io.kamax.mxgwd.config.matrix.Acl;
import io.kamax.mxgwd.config.matrix.Endpoint;
import io.kamax.mxgwd.config.matrix.EntityIO;
import io.kamax.mxgwd.config.matrix.Host;
import io.kamax.mxgwd.model.acl.AclTargetHandler;
import io.kamax.mxgwd.storage.OrmLiteStore;
import io.kamax.mxgwd.storage.Store;
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
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Gateway {

    private final Logger log = LoggerFactory.getLogger(Gateway.class);

    private Config cfg;
    private Store store;

    private ActionMapper actionMapper;
    private AclTargetHandlerMapper aclTargetMapper;
    private EntityMapper entityMapper;

    private CloseableHttpClient client;

    public Gateway(Config cfg) {
        this.cfg = cfg;

        store = new OrmLiteStore(cfg.getStorage());

        actionMapper = new ActionMapper(); // TODO make configurable
        aclTargetMapper = new AclTargetHandlerMapper(); // TODO make configurable
        entityMapper = new EntityMapper(); // TODO make configurable
        client = HttpClients.custom()
                .setMaxConnPerRoute(Integer.MAX_VALUE) // TODO make configurable
                .setMaxConnTotal(Integer.MAX_VALUE) // TODO make configurable
                .setUserAgent("mxgwd/0.0.0") // FIXME use git
                .build();

        processConfig();
    }

    private String decode(String path) {
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private void processConfig() {
        log.info("Config: Processing");
        for (String hostName : cfg.getMatrix().getClient().getHosts().keySet()) {
            Host host = cfg.getMatrix().getClient().getHosts().get(hostName);
            log.info("Host {}: Processing", hostName);
            for (Endpoint endpoint : host.getEndpoints()) {
                for (Acl acl : endpoint.getAcls()) {
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

            log.info("Loading entities from DB");
            EntityIO filter = new EntityIO();
            filter.setHost(hostName);

            List<Long> entities = new ArrayList<>();
            for (EntityIO io : store.findEntity(filter)) {
                log.info("Entity: {} {}", io.getType(), io.getName());
                entities.add(io.getId());
            }
            host.setEntities(entities);
        }
    }

    private AclTargetHandler getAclTargetHandler(String id) {
        return aclTargetMapper.map(id).orElseThrow(() -> new RuntimeException("Unknown ACL target type: " + id));
    }

    public Optional<Host> findHostFor(String name) {
        return Optional.ofNullable(cfg.getMatrix().getClient().getHosts().get(name));
    }

    public Optional<Host> findHostFor(URL url) {
        return findHostFor(url.getAuthority());
    }

    public Host getHostFor(String name) {
        return findHostFor(name).orElseThrow(RuntimeException::new);
    }

    public Host getHostFor(URL url) {
        return getHostFor(url.getAuthority());
    }

    private Optional<String> findAccessTokenInHeaders(Request request) {
        return request.getHeaders().entrySet().stream()
                .filter(e -> StringUtils.equals("Authorization", e.getKey()))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .filter(v -> StringUtils.startsWith(v, "Bearer "))
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
            for (Acl acl : endpoint.getAcls()) {
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

    private Exchange buildExchange(Request request, Host mxHost) {
        Context context = new Context();

        // We build the security context; finding out if the caller is authenticated, its identity, the roles it has
        findAccessToken(request).ifPresent(token -> {
            log.info("Access token found");
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

        // We get all entities and raw endpoints
        List<Endpoint> endpoints = new ArrayList<>();
        mxHost.getEntities().stream()
                .map(id -> store.findEntity(id).orElseThrow(RuntimeException::new))
                .forEach(entity -> endpoints.addAll(entityMapper.map(entity)));
        endpoints.addAll(mxHost.getEndpoints());

        // We try to find a matching raw endpoint
        for (Endpoint endpoint : endpoints) {
            boolean pathMatch;
            String path = decode(request.getUrl().getPath());
            if ("regexp".equals(endpoint.getMatch())) {
                pathMatch = Pattern.compile(endpoint.getPath()).matcher(path).matches();
            } else {
                pathMatch = StringUtils.startsWith(path, endpoint.getPath());
            }

            boolean methodBlank = StringUtils.isBlank(endpoint.getMethod());
            boolean methodMatch = methodBlank || StringUtils.equals(endpoint.getMethod(), request.getMethod());

            if (!pathMatch || !methodMatch) {
                log.info("Endpoint {}:{} is not a match", endpoint.getMethod(), endpoint.getPath());
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
        URL targetHost = ex.getEndpoint().map(Endpoint::getTo).orElse(ex.getHost().getTo());
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
        for (Endpoint endpoint : ex.getHost().getEndpoints()) {
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

    public List<Entity> findEntities(String host) {
        return getHostFor(host).getEntities().stream()
                .map(id -> store.findEntity(id).orElseThrow(RuntimeException::new))
                .map(io -> new Entity(store, io))
                .collect(Collectors.toList());
    }

    public Entity createEntity(EntityIO io) {
        Host host = getHostFor(io.getHost());
        store.insertEntity(io);
        io = store.findEntity(io.getId()).orElseThrow(RuntimeException::new);
        Entity e = new Entity(store, io);
        host.addEntity(e.getId());
        return e;
    }

    public Entity getEntity(long id) {
        return new Entity(store, store.findEntity(id).orElseThrow(IllegalArgumentException::new));
    }

}
