package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.juxtasoftware.dao.UserAnnotationDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.UserAnnotation;
import org.juxtasoftware.model.UserAnnotation.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Range;

@Repository
public class UserAnnotationDaoImpl implements UserAnnotationDao, InitializingBean {
    
    private static final String MAIN_TABLE = "juxta_user_note";
    private static final String DATA_TABLE = "juxta_user_note_data";
    protected SimpleJdbcInsert insert;
    
    @Autowired private JdbcTemplate jdbcTemplate;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(jdbcTemplate).withTableName(MAIN_TABLE).usingGeneratedKeyColumns("id");
    }

    @Override
    public void create(UserAnnotation ua) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("set_id", ua.getSetId());
        ps.addValue("base_id", ua.getBaseId());
        ps.addValue("range_start", ua.getBaseRange().getStart());
        ps.addValue("range_End", ua.getBaseRange().getEnd());
        Long id = this.insert.executeAndReturnKey(ps).longValue();
        addNotes(id, ua.getNotes());
    }

    private void addNotes(Long id, Set<Data> notes) {
        final String sql = "insert into "+DATA_TABLE+" (note_id,witness_id,note) values (?,?,?)";
        for ( UserAnnotation.Data noteData : notes ) {
            this.jdbcTemplate.update(sql, id, noteData.getWitnessId(), noteData.getNote());
        }
    }

    @Override
    public List<UserAnnotation> list(ComparisonSet set, Long baseId, Range r) {
        Extractor rse = new Extractor();

        StringBuilder sql = getFindSql();
        sql.append(" where base_id=?");
        if ( r == null ) {
            return this.jdbcTemplate.query(sql.toString(), rse, baseId );
        }
        
        sql.append(" and range_start>=? and range_end<=?");
        return this.jdbcTemplate.query(sql.toString(), rse, baseId, r.getStart(), r.getEnd() );
    }
    
    private StringBuilder getFindSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("select id,set_id,base_id,range_start,range_end,witness_id,note from ");
        sql.append(MAIN_TABLE);
        sql.append(" inner join ").append(DATA_TABLE);
        sql.append(" on id = note_id ");
        return sql;
    }

    @Override
    public void update(UserAnnotation ua) {
        String sql = "delete from "+DATA_TABLE+" where note_id=?";
        this.jdbcTemplate.update(sql,ua.getId());
        addNotes(ua.getId(), ua.getNotes());
        
    }

    @Override
    public void delete(ComparisonSet set, Long baseId, Range r) {
        if ( baseId == null ) {
            final String sql = "delete from "+MAIN_TABLE+" where set_id=?";
            this.jdbcTemplate.update(sql, set.getId() );
            return;
        } 
        
        if ( r == null ) {
            final String sql = "delete from "+MAIN_TABLE+" where set_id=? and base_id=?";
            this.jdbcTemplate.update(sql, set.getId(), baseId );
            return;
        } 
        
        final String sql = "delete from "+MAIN_TABLE+" where set_id=? and base_id=? and range_start>=? and range_end<=?";
        this.jdbcTemplate.update(sql, set.getId(), baseId, r.getStart(), r.getEnd());
    }

    @Override
    public boolean hasUserAnnotations(ComparisonSet set, Long baseId) {
        String sql = "select count(*) as cnt from "+MAIN_TABLE+" where base_id=? and set_id=?";
        return (this.jdbcTemplate.queryForInt(sql, baseId, set.getId())>0);
    }
    
    @Override
    public int count(ComparisonSet set, Long baseId) {
        String sql = "select count(*) as cnt from "+MAIN_TABLE+" where base_id=? and set_id=?";
        return this.jdbcTemplate.queryForInt(sql, baseId, set.getId());
    }
    
    /**
     * Mapper to convert raw sql data into user annotation class
     */
    private static class Extractor implements ResultSetExtractor< List<UserAnnotation> > {

        @Override
        public List<UserAnnotation> extractData(ResultSet rs) throws SQLException, DataAccessException {
            Map<Long, UserAnnotation> map = new HashMap<Long, UserAnnotation>();
            while ( rs.next() ) {
                Long id = rs.getLong("id");
                UserAnnotation ua = map.get(id);
                if ( ua == null ) {
                    ua = new UserAnnotation();
                    ua.setId(id);
                    ua.setBaseId( rs.getLong("base_id") );
                    ua.setSetId( rs.getLong("set_id") );
                    ua.setBaseRange( new Range(
                        rs.getLong("range_start"),
                        rs.getLong("range_end") ) );
                    map.put(id, ua);
                }
                ua.addNote( rs.getLong("witness_id"), rs.getString("note"));
            }
            return new ArrayList<UserAnnotation>(map.values());
        }
    }

}
