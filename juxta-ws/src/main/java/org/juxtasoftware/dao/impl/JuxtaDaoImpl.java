package org.juxtasoftware.dao.impl;

import org.juxtasoftware.dao.JuxtaDao;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

public abstract class JuxtaDaoImpl<T> implements JuxtaDao<T>, InitializingBean {

    @Autowired
    protected JdbcTemplate jt;
    
    protected final String tableName;
    protected SimpleJdbcInsert insert;

    protected JuxtaDaoImpl(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(jt).withTableName(tableName).usingGeneratedKeyColumns("id");
    }

    @Override
    public long create(final T object) {
        return insert.executeAndReturnKey(toInsertData(object)).longValue();
    }

    protected abstract SqlParameterSource toInsertData(T object);
}
