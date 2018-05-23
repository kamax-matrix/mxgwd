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

import io.kamax.mxgwd.config.Config;
import io.kamax.mxgwd.config.Value;
import io.kamax.mxgwd.config.yaml.YamlConfigLoader;
import io.kamax.mxgwd.model.Gateway;
import io.kamax.mxgwd.undertow.admin.MatrixClientHostListHandler;
import io.kamax.mxgwd.undertow.matrix.client.ActivePoliciesListingHandler;
import io.kamax.mxgwd.undertow.matrix.client.CatchAllHandler;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class UndertowApp {

    public static void main(String[] args) {
        try {
            String cfgFile = StringUtils.defaultIfBlank(System.getenv("MXGWD_CONFIG_FILE"), "mxgwd.yaml");
            Config cfg = Value.get(YamlConfigLoader.loadFromFile(cfgFile), Config::new);
            Gateway gw = new Gateway(cfg);

            HttpHandler allHandler = new BlockingHandler(new CatchAllHandler(gw));
            HttpHandler activePolicies = new BlockingHandler(new ActivePoliciesListingHandler(gw));

            Undertow gwSrv = Undertow.builder()
                    .addHttpListener(cfg.getServer().getPort(), "0.0.0.0")
                    .setHandler(Handlers.path()
                            .addExactPath("/_matrix/client/r0/policy/policies", activePolicies)
                            .addPrefixPath("/", allHandler))
                    .build();

            gwSrv.start();

            Undertow adminSrv = Undertow.builder()
                    .addHttpListener(cfg.getAdmin().getPort(), "0.0.0.0")
                    .setHandler(Handlers.path()
                            .addExactPath("/admin/api/v1/matrix/client/host", new BlockingHandler(new MatrixClientHostListHandler(cfg)))
                    ).build();
            adminSrv.start();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}
