package org.juxtasoftware.dao.impl;

import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.JuxtaXsltDao;
import org.juxtasoftware.model.JuxtaXslt;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Workspace;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class JuxtaXsltDaoImpl extends JuxtaDaoImpl<JuxtaXslt> implements JuxtaXsltDao {

    protected JuxtaXsltDaoImpl() {
        super("juxta_xslt");
    }

    @Override
    public void delete(JuxtaXslt juxtaXslt) {
        if ( juxtaXslt != null ) {
            this.jt.update("delete from " + this.tableName + " where id = ?", juxtaXslt.getId());
        }
    }

    @Override
    public JuxtaXslt find(Long id) {
        final String sql = "select id, workspace_id, name, xslt, default_namespace from juxta_xslt where id=?";
        return DataAccessUtils.uniqueResult(
            this.jt.query(sql, new RowMapper<JuxtaXslt>() {
                
                @Override
                public JuxtaXslt mapRow(ResultSet rs, int rowNum) throws SQLException {
                    JuxtaXslt xslt = new JuxtaXslt();
                    xslt.setId( rs.getLong("id"));
                    xslt.setWorkspaceId( rs.getLong("workspace_id"));
                    xslt.setName( rs.getString("name"));
                    xslt.setDefaultNamespace( rs.getString("default_namespace"));
                    xslt.setXslt( rs.getString("xslt"));
                    return xslt;
                }    
                
            }, id));
    }

    @Override
    public void update(final Long xsltId, final Reader xsltReader) {
        final String sql = "update "+this.tableName+" set xslt=? where id=?";
        this.jt.update(sql, new PreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(2, xsltId);
                ps.setCharacterStream(1, xsltReader);
            }
        });    
    }
    
    @Override
    public List<Usage> getUsage(JuxtaXslt xslt) {
        final String sql = "select id, name from juxta_witness where xslt_id=?";
        List<Usage> usage =  this.jt.query(sql, new RowMapper<Usage>() {

            @Override
            public Usage mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Usage(Usage.Type.WITNESS, rs.getLong("id"), rs.getString("name"));
            }
            }, xslt.getId());
        
        // find all of the sets that use these witnesses. Add these to the initial list
        if ( usage.size() > 0 ) {
            StringBuilder ids = new StringBuilder();
            for (Usage u : usage) {
                if ( ids.length() > 0) {
                    ids.append(",");
                }
                ids.append( u.getId() );
            }
            String setSql = "select distinct set_id,name from juxta_comparison_set_member " +
                    "inner join juxta_comparison_set on juxta_comparison_set.id = set_id " +
                    "where witness_id in ("+ids+")";
            usage.addAll( this.jt.query(setSql, new RowMapper<Usage>(){
                @Override
                public Usage mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new Usage(Usage.Type.COMPARISON_SET, rs.getLong("set_id"), rs.getString("name"));
                }
                
            }));
        }
        
        return usage;
    }

    @Override
    public List<JuxtaXslt> list(Workspace ws) {
        final String sql = "select id, workspace_id, name from juxta_xslt";
        return this.jt.query(sql, new RowMapper<JuxtaXslt>(){
            @Override
            public JuxtaXslt mapRow(ResultSet rs, int rowNum) throws SQLException {
                JuxtaXslt xslt = new JuxtaXslt();
                xslt.setId( rs.getLong("id"));
                xslt.setWorkspaceId( rs.getLong("workspace_id"));
                xslt.setName( rs.getString("name"));
                return xslt;
            }    
        });     
    }

    @Override
    protected SqlParameterSource toInsertData(JuxtaXslt object) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("id", object.getId());
        ps.addValue("workspace_id", object.getWorkspaceId());
        ps.addValue("name", object.getName());
        ps.addValue("default_namespace", object.getDefaultNamespace());
        ps.addValue("xslt", object.getXslt());
        return ps;
    }


}
