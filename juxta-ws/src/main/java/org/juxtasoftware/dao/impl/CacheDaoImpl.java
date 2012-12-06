package org.juxtasoftware.dao.impl;

import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.CacheDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

//FIXME No cachning call should ever throw. If it doesn't work, log the error and
// move on. It will be cached nect time.

@Repository
public class CacheDaoImpl implements CacheDao {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Integer cacheLifespan;
    
    protected static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME ); 
    private final String TABLE = "juxta_collation_cache";
    
    @Override
    public void deleteAll(Long setId) {
        final String sql = "delete from "+TABLE+" where set_id=?";
        jdbcTemplate.update(sql, setId );
    }
    
    @Override
    public boolean heatmapExists(final Long setId, final Long key, boolean condensed ) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and config=? and data_type=?";
        String type = "HEATMAP";
        if ( condensed ) {
            type = "CONDENSED_HEATMAP";
        }
        long cnt = jdbcTemplate.queryForLong(sql, setId, key.toString(), type);
        return cnt > 0;
    }
    
    @Override
    public void deleteHeatmap(final Long setId) {
        final String sql = "delete from "+TABLE+" where set_id=? and data_type=? || data_type=?";
        jdbcTemplate.update(sql, setId, "HEATMAP", "CONDENSED_HEATMAP");
    }

    @Override
    public Reader getHeatmap(final Long setId, final Long key, final boolean condensed ) {
        final String sql = "select data from "
            +TABLE+" where set_id=? and config=? and data_type=?";
        String type = "HEATMAP";
        if ( condensed ) {
            type = "CONDENSED_HEATMAP";
        }
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, key.toString(), type) );
    }

    @Override
    public void cacheHeatmap(final Long setId, final Long key, final Reader data, final boolean condensed ) {
        try {
            final String sql = "insert into " + TABLE+ " (set_id, config, data_type, data) values (?,?,?,?)";
            this.jdbcTemplate.update(sql, new PreparedStatementSetter() {
    
                @Override
                public void setValues(PreparedStatement ps) throws SQLException {
                    String type = "HEATMAP";
                    if ( condensed ) {
                        type = "CONDENSED_HEATMAP";
                    }
                    ps.setLong(1, setId);
                    ps.setString(2, key.toString());
                    ps.setString(3, type);
                    ps.setCharacterStream(4, data);
                }
            }); 
        } catch (Exception e) {
            LOG.error("Unable to cache heatmap for set "+setId, e);
        }
    }
    
    @Override
    public boolean exportExists(  final Long setId, final Long baseId  ) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and config=? and data_type=?";
        long cnt = jdbcTemplate.queryForLong(sql, setId, baseId.toString(), "EXPORT");
        return cnt > 0;
    }
    
    @Override
    public Reader getExport( final Long setId, final Long baseId ) {
        final String sql = "select data from "+TABLE+" where set_id=? and config=? and data_type=?";
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, baseId.toString(), "EXPORT") );
    }
    
    @Override
    public void cacheExport( final Long setId, final Long baseId, final Reader data) {
        final String sql = "insert into " + TABLE+ " (set_id, config, data_type, data) values (?,?,?,?)";
        this.jdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(1, setId);
                ps.setString(2, baseId.toString());
                ps.setString(3, "EXPORT");
                ps.setCharacterStream(4, data);
            }
        });  
    }
    
    @Override
    public boolean criticalApparatusExists(  final Long setId, final Long baseId  ) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and config=? and data_type=?";
        long cnt = jdbcTemplate.queryForLong(sql, setId, baseId.toString(), "CRITICAL_APPARATUS");
        return cnt > 0;
    }
    
    @Override
    public Reader getCriticalApparatus( final Long setId, final Long baseId ) {
        final String sql = "select data from "+TABLE+" where set_id=? and config=? and data_type=?";
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, baseId.toString(), "CRITICAL_APPARATUS") );
    }
    
    @Override
    public void cacheCriticalApparatus( final Long setId, final Long baseId, final Reader data) {
        final String sql = "insert into " + TABLE+ " (set_id, config, data_type, data) values (?,?,?,?)";
        this.jdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(1, setId);
                ps.setString(2, baseId.toString());
                ps.setString(3, "CRITICAL_APPARATUS");
                ps.setCharacterStream(4, data);
            }
        });  
    }
    
    @Override
    public boolean histogramExists( final Long setId, final Long key   ) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and config=? and data_type=?";
        long cnt = jdbcTemplate.queryForLong(sql, setId, key.toString(), "HISTOGRAM");
        return cnt > 0;
    }

    @Override
    public Reader getHistogram(final Long setId, final Long key ) {
        final String sql = "select data from "
            +TABLE+" where set_id=? and config=? and data_type=?";
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, key.toString(), "HISTOGRAM") );
    }

    @Override
    public void cacheHistogram(final Long setId, final Long key, final Reader data) {
        final String sql = "insert into " + TABLE+ " (set_id, config, data_type, data) values (?,?,?,?)";
        this.jdbcTemplate.update(sql, new PreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps) throws SQLException {
                ps.setLong(1, setId);
                ps.setString(2, key.toString());
                ps.setString(3, "HISTOGRAM");
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
        try {
            final String sql = "insert into " + TABLE+ " (set_id, config, data_type, data) values (?,?,?,?)";
            this.jdbcTemplate.update(sql, new PreparedStatementSetter() {
    
                @Override
                public void setValues(PreparedStatement ps) throws SQLException {
                    ps.setLong(1, setId);
                    ps.setString(2, witness1.toString()+","+witness2.toString());
                    ps.setString(3, "SIDEBYSIDE");
                    ps.setCharacterStream(4, data);
                }
            });  
        } catch (Exception e) {
            LOG.error("Unable to cache side-by-side for set "+setId+" witnesses "+witness1+","+witness2, e);
        }
    }

    @Override
    public boolean sideBySideExists(Long setId, Long witness1, Long witness2) {
        final String sql = "select count(*) as cnt from "
            +TABLE+" where set_id=? and config=? and data_type=?";
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
            +TABLE+" where set_id=? and config=? and data_type=?";
        final String witnessList = toList(witness1, witness2);
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(sql, new RowMapper<Reader>(){

                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("data");
                }
                
            }, setId, witnessList, "SIDEBYSIDE") );
    }
    
    @Override
    public void purgeExpired() {
        final String sql = "delete from juxta_collation_cache where created < ( NOW() - INTERVAL "+this.cacheLifespan+" HOUR)";
        this.jdbcTemplate.update(sql);
    }
}
