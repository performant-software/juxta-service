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

@Repository
public class CacheDaoImpl implements CacheDao {
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Integer cacheLifespan;
    
    protected static final Logger LOG = LoggerFactory.getLogger( Constants.WS_LOGGER_NAME ); 
    private final String TABLE = "juxta_collation_cache";
    
    @Override
    public void deleteAll(Long setId) {
        try {
            final String sql = "delete from "+TABLE+" where set_id=?";
            jdbcTemplate.update(sql, setId );
        } catch (Exception e) {
            LOG.error("Clear cache failed for set "+setId, e);
        }
    }
    
    @Override
    public boolean heatmapExists(final Long setId, final Long key, boolean condensed ) {
        try {
            final String sql = "select count(*) as cnt from "
                +TABLE+" where set_id=? and config=? and data_type=?";
            String type = "HEATMAP";
            if ( condensed ) {
                type = "CONDENSED_HEATMAP";
            }
            long cnt = jdbcTemplate.queryForLong(sql, setId, key.toString(), type);
            return cnt > 0;
        } catch (Exception e) {
            LOG.error("Cached heatmap exists failed for set "+setId, e);
            return false;
        }
    }
    
    @Override
    public void deleteHeatmap(final Long setId) {
        try {
            final String sql = "delete from "+TABLE+" where set_id=? and data_type=? || data_type=?";
            jdbcTemplate.update(sql, setId, "HEATMAP", "CONDENSED_HEATMAP");
        } catch (Exception e) {
            LOG.error("Unable to delete cached heatmap for set "+setId, e);
        }
    }

    @Override
    public Reader getHeatmap(final Long setId, final Long key, final boolean condensed ) {
        try {
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
        } catch (Exception e) {
            LOG.error("Unable to retrieve cached heatmap for set "+setId, e);
            return null;
        }
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
        try {
            final String sql = "select count(*) as cnt from "
                +TABLE+" where set_id=? and config=? and data_type=?";
            long cnt = jdbcTemplate.queryForLong(sql, setId, baseId.toString(), "EXPORT");
            return cnt > 0;
        } catch (Exception e) {
            LOG.error("Export exists failed for set "+setId, e);
            return false;
        }
    }
    
    @Override
    public Reader getExport( final Long setId, final Long baseId ) {
        try {
            final String sql = "select data from "+TABLE+" where set_id=? and config=? and data_type=?";
            return DataAccessUtils.uniqueResult(
                this.jdbcTemplate.query(sql, new RowMapper<Reader>(){
    
                    @Override
                    public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return rs.getCharacterStream("data");
                    }
                    
                }, setId, baseId.toString(), "EXPORT") );
        } catch (Exception e) {
            LOG.error("Retrieve cached export failed for set "+setId, e);
            return null;
        }
    }
    
    @Override
    public void cacheExport( final Long setId, final Long baseId, final Reader data) {
        try {
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
        } catch (Exception e) {
            LOG.error("Cache export failed for set "+setId, e);
        }
    }
    
    @Override
    public boolean editionExists(  final Long setId, final long token ) {
        try {
            final String sql = "select count(*) as cnt from "
                +TABLE+" where set_id=? and config=? and data_type=?";
            long cnt = jdbcTemplate.queryForLong(sql, setId, token, "EDITION");
            return cnt > 0;
        } catch (Exception e) {
            LOG.error("Edition exists failed for set "+setId, e);
            return false;
        }
    }
    
    @Override
    public Reader getEdition( final Long setId,  final long token  ) {
        try {
            final String sql = "select data from "+TABLE+" where set_id=? and config=? and data_type=?";
            return DataAccessUtils.uniqueResult(
                this.jdbcTemplate.query(sql, new RowMapper<Reader>(){
    
                    @Override
                    public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return rs.getCharacterStream("data");
                    }
                    
                }, setId, token, "EDITION") );
        } catch (Exception e) {
            LOG.error("Unable to get Edition for set "+setId, e);
            return null;
        }
    }
    
    @Override
    public void cacheEdition( final Long setId, final long token, final Reader data) {
        try {
            final String sql = "insert into " + TABLE+ " (set_id, config, data_type, data) values (?,?,?,?)";
            this.jdbcTemplate.update(sql, new PreparedStatementSetter() {
    
                @Override
                public void setValues(PreparedStatement ps) throws SQLException {
                    ps.setLong(1, setId);
                    ps.setString(2, Long.toString(token) );
                    ps.setString(3, "EDITION");
                    ps.setCharacterStream(4, data);
                }
            });  
        } catch (Exception e) {
            LOG.error("Cache Edition failed for set "+setId, e);
        }
    }
    
    @Override
    public boolean histogramExists( final Long setId, final Long key   ) {
        try {
            final String sql = "select count(*) as cnt from "
                +TABLE+" where set_id=? and config=? and data_type=?";
            long cnt = jdbcTemplate.queryForLong(sql, setId, key.toString(), "HISTOGRAM");
            return cnt > 0;
        } catch (Exception e) {
            LOG.error("Check histogram failed for set "+setId, e);
            return false;
        }
    }

    @Override
    public Reader getHistogram(final Long setId, final Long key ) {
        try {
            final String sql = "select data from "
                +TABLE+" where set_id=? and config=? and data_type=?";
            return DataAccessUtils.uniqueResult(
                this.jdbcTemplate.query(sql, new RowMapper<Reader>(){
    
                    @Override
                    public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                        return rs.getCharacterStream("data");
                    }
                    
                }, setId, key.toString(), "HISTOGRAM") );
        } catch (Exception e) {
            LOG.error("Unable to get histogram for set "+setId, e);
            return null;
        }
    }

    @Override
    public void cacheHistogram(final Long setId, final Long key, final Reader data) {
        try {
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
        } catch (Exception e) {
            LOG.error("Unable to cache histogram for set "+setId, e);
        }
    }

    @Override
    public void deleteSideBySide(Long setId) {
        try {
            final String sql = "delete from "+TABLE+" where set_id=? and data_type=?";
            jdbcTemplate.update(sql, setId, "SIDEBYSIDE");
        } catch (Exception e) {
            LOG.error("Unable to delete side-by-side for set "+setId, e);
        }
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
        try {
            final String sql = "select count(*) as cnt from "
                +TABLE+" where set_id=? and config=? and data_type=?";
            final String wits = toList(witness1, witness2);
            long cnt = jdbcTemplate.queryForLong(sql, setId, wits, "SIDEBYSIDE");
            return cnt > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private String toList(final Long witness1, final Long witess2) {
        return witness1.toString()+","+witess2.toString();
    }

    @Override
    public Reader getSideBySide(Long setId, Long witness1, Long witness2) {
        try {
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
        } catch (Exception e) {
            LOG.error("Unable to retrieve cached side-by-side for set "+setId+" witnesses "+witness1+","+witness2, e);
            return null;
        }
    }
    
    @Override
    public void purgeExpired() {
        if ( this.cacheLifespan < 0 ) {
            LOG.info("Cache set to never expire; not purging");
            return;
        }
        try {
            final String sql = "delete from juxta_collation_cache where permanent=0 and created < ( NOW() - INTERVAL "+this.cacheLifespan+" HOUR)";
            this.jdbcTemplate.update(sql);
        } catch (Exception e) {
            LOG.error("Unable to purge expired cache", e);
        }
    }
}
