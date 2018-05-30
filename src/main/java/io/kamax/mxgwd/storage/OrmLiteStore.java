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

package io.kamax.mxgwd.storage;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import io.kamax.mxgwd.config.Storage;
import io.kamax.mxgwd.config.matrix.EntityIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class OrmLiteStore implements Store {

    private final Logger log = LoggerFactory.getLogger(OrmLiteStore.class);

    private interface Getter<T> {

        T get() throws SQLException, IOException;

    }

    private interface Doer {

        void run() throws SQLException, IOException;

    }

    private Dao<EntityIO, Long> entityDao;

    public OrmLiteStore(Storage cfg) {
        withCatcher(() -> {
            ConnectionSource connPool = new JdbcConnectionSource("jdbc:" + cfg.getType() + ":" + cfg.getLocation());
            entityDao = createDaoAndTable(connPool, EntityIO.class);
        });
    }

    private <T> T withCatcher(Getter<T> g) {
        try {
            return g.get();
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e); // FIXME do better
        }
    }

    private void withCatcher(Doer d) {
        try {
            d.run();
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e); // FIXME do better
        }
    }

    private <V, K> Dao<V, K> createDaoAndTable(ConnectionSource connPool, Class<V> c) throws SQLException {
        Dao<V, K> dao = DaoManager.createDao(connPool, c);
        TableUtils.createTableIfNotExists(connPool, c);
        return dao;
    }

    @Override
    public Optional<EntityIO> findEntity(long id) {
        return withCatcher(() -> Optional.ofNullable(entityDao.queryForId(id)));
    }

    @Override
    public EntityIO insertEntity(EntityIO io) {
        log.info("Storing new entity");
        return withCatcher(() -> {
            int updated = entityDao.create(io);
            if (updated != 1) {
                throw new RuntimeException("Unexpected row count after DB action: " + updated);
            }
            return io;
        });
    }

    @Override
    public void updateEntity(EntityIO io) {
        log.info("Updating entity {}", io.getId());
        withCatcher(() -> {
            int updated = entityDao.update(io);
            if (updated != 1) {
                throw new RuntimeException("Unexpected row count after DB action: " + updated);
            }
        });
    }

    @Override
    public void deleteEntity(long id) {
        log.info("Deleting entity {}", id);
        withCatcher(() -> {
            int updated = entityDao.deleteById(id);
            if (updated != 1) {
                throw new RuntimeException("Unexpected row count after DB action: " + updated);
            }
        });
    }

    @Override
    public List<EntityIO> findEntity(EntityIO filter) {
        return withCatcher(() -> entityDao.queryForMatchingArgs(filter));
    }

}
