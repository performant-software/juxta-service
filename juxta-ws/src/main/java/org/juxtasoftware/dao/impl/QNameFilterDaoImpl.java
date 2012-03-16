package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.juxtasoftware.dao.QNameFilterDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Workspace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Name;
import eu.interedition.text.rdbms.RelationalName;

@Repository
public class QNameFilterDaoImpl  extends JuxtaDaoImpl<QNameFilter> implements QNameFilterDao {

    @Autowired WorkspaceDao workspaceDao;
    public static final String MEMBER_TABLE_NAME = "juxta_qname_filter_member";
    private SimpleJdbcInsert memberInsert;
    
    protected QNameFilterDaoImpl() {
        super("juxta_qname_filter");
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.memberInsert = new SimpleJdbcInsert(this.jt).withTableName(MEMBER_TABLE_NAME);
    }
    
    @Override
    public long create(QNameFilter filter) {
        Long id = super.create(filter);
        filter.setId( id );
        insertQnames(filter);
        return id;
    }

    private void insertQnames(QNameFilter filter) {
        if ( filter.getQNames().size() > 0 ) {
            final List<SqlParameterSource> batch = new ArrayList<SqlParameterSource>();
            for ( Name qname : filter.getQNames()) {
                MapSqlParameterSource ps = new MapSqlParameterSource();
                ps.addValue("filter_id", filter.getId() );
                ps.addValue("qname_id", ((RelationalName)qname).getId() );
                batch.add(ps);
            }
            this.memberInsert.executeBatch(batch.toArray(new SqlParameterSource[batch.size()]));
        }
    }
    
    @Override
    public void update(QNameFilter filter) {
        jt.update("update " + this.tableName + " set name = ? where id = ?", filter.getName(), filter.getId());
        jt.update("delete from " + MEMBER_TABLE_NAME + " where filter_id = ?", filter.getId());
        insertQnames(filter);
    }

    @Override
    public void delete(QNameFilter filter) {
        final String sql = "delete from " + this.tableName+" where id=?";
        this.jt.update(sql, filter.getId());
    }
    
    @Override
    public QNameFilter find(String name) {
        Workspace w = this.workspaceDao.getPublic();
        return find( w, name);
    }
    
    @Override
    public QNameFilter find(final Workspace workspace, final String name) {
        final String sql = "select id,name,workspace_id from " + this.tableName + 
            " where name=? and workspace_id=?";
        final QNameFilter filter = DataAccessUtils.uniqueResult(
            this.jt.query(sql, new FilterMapper(), name, workspace.getId()));        
        addFilterMembers(filter);
        return filter;
    }

    private void addFilterMembers( QNameFilter filter) {
        if ( filter != null ) {
            final String memberSql = "select text_qname.id, local_name, namespace from "+
                MEMBER_TABLE_NAME+" inner join text_qname on text_qname.id = qname_id" +
                " where filter_id=?";
            List<Name> names = this.jt.query(memberSql, new QnameMapper(), filter.getId());
            filter.setQNames( new HashSet<Name>( names) );
        }
    }

    @Override
    public QNameFilter find(Long id) {
        final String sql = "select id,name,workspace_id from " + this.tableName + " where id=?";
        final QNameFilter filter = DataAccessUtils.uniqueResult(
            this.jt.query(sql, new FilterMapper(), id));
        addFilterMembers(filter);
        return filter;
    }

    @Override
    public List<QNameFilter> list( final Workspace ws) {
        final String sql = "select id,name,workspace_id from "+this.tableName+" where workspace_id=?";
        return this.jt.query(sql, new FilterMapper(), ws.getId() );
    }

    @Override
    protected SqlParameterSource toInsertData(QNameFilter object) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", object.getName());
        ps.addValue("workspace_id", object.getWorkspaceId());
        return ps;
    }
    
    /**
     * Map qname filter db to model
     * @author loufoster
     */
    private static class FilterMapper implements RowMapper<QNameFilter> {
        public QNameFilter mapRow(ResultSet rs, int rowNum) throws SQLException {
            QNameFilter filter = new QNameFilter();
            filter.setId(rs.getLong("id"));
            filter.setName(rs.getString("name"));
            filter.setWorkspaceId(rs.getLong("workspace_id"));
            return filter;
        }
    }
    
    /**
     * Map Qname db to model
     * @author loufoster
     */
    private static class QnameMapper implements RowMapper<Name> {
        public Name mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new RelationalName(rs.getString("namespace"), rs.getString("local_name"), rs.getInt("id"));
        }
    }

}
