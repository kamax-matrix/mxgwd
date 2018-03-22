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

package io.kamax.matrix.gw.spring.controller;

import io.kamax.matrix.gw.model.MatrixGateway;
import io.kamax.matrix.gw.model.Request;
import io.kamax.matrix.gw.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

@Controller
public class CatchAllController {

    private MatrixGateway gw;

    @Autowired
    public CatchAllController(MatrixGateway gw) {
        this.gw = gw;
    }

    @RequestMapping(path = "/**")
    @ResponseBody
    public String catchAll(HttpServletRequest reqIn, HttpServletResponse resOut) throws IOException, URISyntaxException {
        URL reqInUrl = new URL(reqIn.getRequestURL().toString());

        Map<String, List<String>> headers = new HashMap<>();
        Collections.list(reqIn.getHeaderNames()).forEach(name -> headers.put(name, Collections.list(reqIn.getHeaders(name))));

        Map<String, List<String>> parameters = new HashMap<>();
        reqIn.getParameterMap().forEach((k, v) -> parameters.put(k, Arrays.asList(v)));

        Request reqOut = new Request();
        reqOut.setMethod(reqIn.getMethod());
        reqOut.setUrl(reqInUrl);
        reqOut.setQuery(parameters);
        reqOut.setHeaders(headers);

        Response resIn = gw.execute(reqOut);
        resOut.setStatus(resIn.getStatus());
        resIn.getHeaders().forEach((k, v) -> v.forEach(vv -> resOut.addHeader(k, vv)));
        return resIn.getBody().orElse(null);
    }

}
