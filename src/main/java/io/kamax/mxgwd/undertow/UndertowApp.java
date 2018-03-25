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
import io.undertow.Handlers;
import io.undertow.Undertow;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class UndertowApp {

    public static void main(String[] args) throws IOException {
        String cfgFile = StringUtils.defaultIfBlank(System.getenv("MXGWD_CONFIG_FILE"), "mxgwd.yaml");
        Config cfg = Value.get(YamlConfigLoader.loadFromFile(cfgFile), Config::new);
        Gateway gw = new Gateway(cfg);

        CatchAllHandler allHandler = new CatchAllHandler(gw);
        ActivePoliciesListingHandler activePolicies = new ActivePoliciesListingHandler(gw);

        Undertow server = Undertow.builder()
                .addHttpListener(cfg.getServer().getPort(), "0.0.0.0")
                .setHandler(Handlers.path()
                        .addExactPath("/_matrix/client/r0/policy/policies", activePolicies)
                        .addPrefixPath("/", allHandler))
                .build();

        server.start();
    }

}
