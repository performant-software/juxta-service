package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Workspace;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceDaoImpl  extends JuxtaDaoImpl<Workspace> implements WorkspaceDao {

    protected WorkspaceDaoImpl() {
        super("juxta_workspace");
    }

    @Override
    public void delete(Workspace workspace) {
        final String sql = "delete from " + this.tableName + " where id=?";
        this.jt.update(sql, workspace.getId());
    }

    @Override
    public Workspace find(Long id) {
        final StringBuilder sql = getSql();
        sql.append(" where id=?");
        return DataAccessUtils.uniqueResult(
            this.jt.query(sql.toString(), new WorkspaceMapper(), id));
    }
    
    @Override
    public Workspace find(final String name) {
        final StringBuilder sql = getSql();
        sql.append(" where name=?");
        return DataAccessUtils.uniqueResult(
            this.jt.query(sql.toString(), new WorkspaceMapper(), name));
    }

    @Override
    public int getWorkspaceCount() {
        final String sql = "select count(*) as cnt from "+this.tableName;
        return this.jt.queryForInt(sql);
    }
    
    @Override
    public List<Workspace> list() {
        final String sql = getSql().toString();
        return this.jt.query(sql, new WorkspaceMapper() );
    }

    @Override
    public Workspace getPublic() {
        return find("public");
    }

    private StringBuilder getSql() {
        StringBuilder sql = new StringBuilder("select id, name, description from ");
        sql.append(this.tableName);
        return sql;
    }
    @Override
    protected SqlParameterSource toInsertData(Workspace workspace) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", workspace.getName());
        ps.addValue("description", workspace.getDescription());
        return ps;
    }
    
    private static class WorkspaceMapper implements RowMapper<Workspace> {
        @Override
        public Workspace mapRow(ResultSet rs, int rowNum) throws SQLException {
            Workspace ws = new Workspace();
            ws.setId( rs.getLong("id"));
            ws.setName( rs.getString("name"));
            ws.setDescription( rs.getString("description") );
            return ws;
        }
    }

}
