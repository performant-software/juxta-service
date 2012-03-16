package org.juxtasoftware.dao.impl;

import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.juxtasoftware.dao.CacheDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CacheDaoImpl implements CacheDao {
    @Autowired JdbcTemplate jdbcTemplate;
    
    private final String TABLE = "juxta_collation_cache";
    
    
    @Override
    public boolean heatmapExists(final Long setId, final Long baseId) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and witness_list=? and data_type=?";
        long cnt = jdbcTemplate.queryForLong(sql, setId, baseId.toString(), "HEATMAP");
        return cnt > 0;
    }
    
    @Override
    public void deleteHeatmap(final Long setId) {
        final String sql = "delete from "+TABLE+" where set_id=? and data_type=?";
        jdbcTemplate.update(sql, setId, "HEATMAP");
    }

    @Override
    public Reader getHeatmap(final Long setId, final Long baseId) {
        final String sql = "select data from "
            +TABLE+" where set_id=? and witness_list=? and data_type=?";
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, baseId.toString(), "HEATMAP") );
    }

    @Override
    public void cacheHeatmap(final Long setId, final Long baseId, final Reader data) {
        final String sql = "insert into " + TABLE+ " (set_id, witness_list, data_type, data) values (?,?,?,?)";
        this.jdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(1, setId);
                ps.setString(2, baseId.toString());
                ps.setString(3, "HEATMAP");
                ps.setCharacterStream(4, data);
            }
        });    
    }

    @Override
    public void deleteSideBySide(Long setId) {
        final String sql = "delete from "+TABLE+" where set_id=? and data_type=?";
        jdbcTemplate.update(sql, setId, "SIDEBYSIDE");
    }

    @Override
    public void cacheSideBySide(final Long setId, final Long witness1, final Long witness2, final Reader data) {
        final String sql = "insert into " + TABLE+ " (set_id, witness_list, data_type, data) values (?,?,?,?)";
        this.jdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(1, setId);
                ps.setString(2, witness1.toString()+","+witness2.toString());
                ps.setString(3, "SIDEBYSIDE");
                ps.setCharacterStream(4, data);
            }
        });  
    }

    @Override
    public boolean sideBySideExists(Long setId, Long witness1, Long witness2) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and witness_list=? and data_type=?";
        final String wits = toList(witness1, witness2);
        long cnt = jdbcTemplate.queryForLong(sql, setId, wits, "SIDEBYSIDE");
        return cnt > 0;
    }
    
    private String toList(final Long witness1, final Long witess2) {
        return witness1.toString()+","+witess2.toString();
    }

    @Override
    public Reader getSideBySide(Long setId, Long witness1, Long witness2) {
        final String sql = "select data from "
            +TABLE+" where set_id=? and witness_list=? and data_type=?";
        final String witnessList = toList(witness1, witness2);
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, witnessList, "SIDEBYSIDE") );
    }
}
