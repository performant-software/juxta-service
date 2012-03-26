package org.juxtasoftware.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.PageBreakDao;
import org.juxtasoftware.model.PageBreak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PageBreakDaoImpl implements PageBreakDao {
    private static final String TABLE_NAME = "juxta_page_break";
    @Autowired private JdbcTemplate jdbcTemplate;

    @Override
    public void create(final List<PageBreak> pbs) {
        this.jdbcTemplate.batchUpdate(
            "insert into "+TABLE_NAME+" (witness_id, offset, label) values (?,?,?)",
            new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, pbs.get(i).getWitnessId());
                    ps.setLong(2, pbs.get(i).getOffset());
                    ps.setString(3, pbs.get(i).getLabel());
                    
                }

                @Override
                public int getBatchSize() {
                    return pbs.size();
                }
                
            } );
    }
    
    @Override
    public boolean hasBreaks(Long witnessId) {
        final String sql = "select count(*) as cnt from "+TABLE_NAME+
            " where witness_id=?";
        try {
            int cnt = this.jdbcTemplate.queryForInt(sql, witnessId);
            return (cnt > 0);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void deleteAll(Long witnessId) {
        final String sql = "delete from "+TABLE_NAME+" where witness_id = ?";
        this.jdbcTemplate.update(sql, witnessId);
    }

    @Override
    public List<PageBreak> find(Long witnessId) {
        final StringBuilder sql = new StringBuilder();
        sql.append( "select id,witness_id,offset,label");
        sql.append(" from ").append(TABLE_NAME);
        sql.append(" where witness_id=? order by offset asc");
        return this.jdbcTemplate.query(sql.toString(), new RowMapper<PageBreak>(){

            @Override
            public PageBreak mapRow(ResultSet rs, int rowNum) throws SQLException {
                PageBreak pb  = new PageBreak();
                pb.setId( rs.getLong("id"));
                pb.setWitnessId( rs.getLong("witness_id"));
                pb.setOffset( rs.getLong("offset"));
                pb.setLabel( rs.getString("label"));
                return pb;
            }
            
        }, witnessId);
    }

}
