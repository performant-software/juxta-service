package org.juxtasoftware.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.PageMarkDao;
import org.juxtasoftware.model.PageMark;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PageMarkDaoImpl implements PageMarkDao {
    private static final String TABLE_NAME = "juxta_page_mark";
    @Autowired private JdbcTemplate jdbcTemplate;

    @Override
    public void create(final List<PageMark> pbs) {
        if ( pbs.isEmpty() ) {
            return;
        }
        this.jdbcTemplate.batchUpdate(
            "insert into "+TABLE_NAME+" (witness_id, offset, label, mark_type) values (?,?,?,?)",
            new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, pbs.get(i).getWitnessId());
                    ps.setLong(2, pbs.get(i).getOffset());
                    ps.setString(3, pbs.get(i).getLabel());
                    ps.setString(4, pbs.get(i).getType().toString());
                    
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
            " where witness_id=? and mark_type=?";
        try {
            int cnt = this.jdbcTemplate.queryForInt(sql, witnessId, PageMark.Type.PAGE_BREAK.toString());
            return (cnt > 0);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public boolean hasLineNumbers(Long witnessId) {
        final String sql = "select count(*) as cnt from "+TABLE_NAME+
            " where witness_id=? and mark_type=?";
        try {
            int cnt = this.jdbcTemplate.queryForInt(sql, witnessId, PageMark.Type.LINE_NUMBER.toString());
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
    public List<PageMark> find(final Long witnessId ) {
        return find(witnessId, null);
    }

    @Override
    public List<PageMark> find(final Long witnessId, final PageMark.Type type) {
        final StringBuilder sql = new StringBuilder();
        sql.append( "select id,witness_id,offset,label,mark_type");
        sql.append(" from ").append(TABLE_NAME);
        sql.append(" where witness_id=?");
        if ( type != null ) {
            sql.append(" and mark_type = ?");
        }
        sql.append(" order by offset asc");
        RowMapper<PageMark> rm = new RowMapper<PageMark>(){

            @Override
            public PageMark mapRow(ResultSet rs, int rowNum) throws SQLException {
                PageMark mark  = new PageMark();
                mark.setId( rs.getLong("id"));
                mark.setWitnessId( rs.getLong("witness_id"));
                mark.setOffset( rs.getLong("offset"));
                mark.setLabel( rs.getString("label"));
                mark.setType( PageMark.Type.valueOf(rs.getString("mark_type"))  );
                return mark;
            }
            
        };
        if  ( type != null ) {
            return this.jdbcTemplate.query(sql.toString(), rm, witnessId, type.toString());
        } else {
            return this.jdbcTemplate.query(sql.toString(), rm, witnessId);
        }
    }

}
