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

import io.kamax.matrix.json.GsonUtil;
import io.kamax.mxgwd.config.Storage;
import io.kamax.mxgwd.config.matrix.AclType;
import io.kamax.mxgwd.config.matrix.EntityIO;
import io.kamax.mxgwd.storage.OrmLiteStore;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

public class OrmLiteStoreTest {

    private OrmLiteStore store;

    @Before
    public void before() {
        Storage cfg = new Storage();
        cfg.setType("sqlite");
        cfg.setLocation(":memory:");

        store = new OrmLiteStore(cfg);
    }

    private EntityIO generateIo() {
        String host = "host" + System.currentTimeMillis();
        String name = "name" + System.currentTimeMillis();
        String type = "type" + System.currentTimeMillis();
        String aclType = AclType.Blacklist.name();
        String aclTarget = "aclTarget" + System.currentTimeMillis();
        String aclValue = "aclValue" + System.currentTimeMillis();

        EntityIO io = new EntityIO();
        io.setHost(host);
        io.setName(name);
        io.setType(type);
        io.setAclType(aclType);
        io.setAclTarget(aclTarget);
        io.setAclValue(aclValue);

        return io;
    }

    private EntityIO doInsert(EntityIO io) {
        store.insertEntity(io);
        return io;
    }

    @Test
    public void insert() {
        EntityIO io = generateIo();
        assertNull(io.getId());

        doInsert(io);
        assertTrue(io.getId() > 0);
    }

    @Test
    public void insertGet() {
        EntityIO io1 = generateIo();
        doInsert(io1);
        EntityIO io2 = store.findEntity(io1.getId()).orElseThrow(RuntimeException::new);
        assertTrue(StringUtils.equals(GsonUtil.getPrettyForLog(io1), GsonUtil.getPrettyForLog(io2)));
    }

}
