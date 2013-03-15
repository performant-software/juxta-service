package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
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
    public Long create(UserAnnotation ua) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("set_id", ua.getSetId());
        ps.addValue("group_id", ua.getGroupId());
        ps.addValue("base_id", ua.getBaseId());
        ps.addValue("range_start", ua.getBaseRange().getStart());
        ps.addValue("range_End", ua.getBaseRange().getEnd());
        Long id = this.insert.executeAndReturnKey(ps).longValue();
        addNotes(id, ua.getNotes());
        ua.setId(id);
        return id;
    }

    private void addNotes(Long id, Set<Data> notes) {
        final String sql = "insert into "+DATA_TABLE+" (note_id,is_group,witness_id,note) values (?,?,?,?)";
        for ( UserAnnotation.Data noteData : notes ) {
            this.jdbcTemplate.update(sql, id, noteData.isGroup(), noteData.getWitnessId(), noteData.getText());
        }
    }
    
    @Override
    public Long findGroupId(ComparisonSet set, Long baseId, Range r) {
        StringBuilder sql = new StringBuilder("select group_id from ");
        sql.append(MAIN_TABLE);
        sql.append(" where set_id=? and base_id=?");        
        sql.append(" and range_start=? and range_end=? and group_id IS NOT NULL");
        return this.jdbcTemplate.queryForLong(sql.toString(), set.getId(), baseId, r.getStart(), r.getEnd() );
    }
    
    @Override
    public UserAnnotation find(ComparisonSet set, Long baseId, Range r) {
        StringBuilder sql = getFindSql();
        sql.append(" where set_id=? and base_id=?");        
        sql.append(" and range_start >= ? and range_end <= ?");
        Extractor rse = new Extractor();
        
        List<UserAnnotation> hits = this.jdbcTemplate.query(sql.toString(), rse, set.getId(), baseId, r.getStart(), r.getEnd() );
        if ( hits.size() == 0 ) {
            return null;
        }
        
        if ( hits.size() == 1 ) {
            return hits.get(0);
        }
        
        UserAnnotation anno =  null;
        for ( UserAnnotation a : hits ) {
            if ( anno == null ) {
                anno = a;
            } else {
                if (anno.getGroupId() == null ) {
                    anno.setGroupId(a.getGroupId());
                }
                anno.addNotes(a.getNotes());
            }
        }
        return anno;
    }

    @Override
    public List<UserAnnotation> list(ComparisonSet set, Long baseId) {
        Extractor rse = new Extractor();
        StringBuilder sql = getFindSql();
        sql.append(" where set_id=? and base_id=?");
        return this.jdbcTemplate.query(sql.toString(), rse, set.getId(), baseId );
    }
    
    private StringBuilder getFindSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("select n.id as n_id,set_id,base_id,range_start,range_end,group_id,d.id as d_id,is_group,witness_id,note from ");
        sql.append(MAIN_TABLE).append(" n");
        sql.append(" inner join ").append(DATA_TABLE).append(" d");
        sql.append(" on n.id = note_id ");
        return sql;
    }
    
    @Override
    public List<Long> getGroupWitnesses(Long groupId) {
        String sql= "select witness_id from juxta_user_note_data where note_id in (select id from juxta_user_note where group_id = ?)";
        return this.jdbcTemplate.queryForList(sql, Long.class, groupId);
    }
    
    @Override
    public void updateGroupId(UserAnnotation ua, Long groupId) {
        String sql="update "+MAIN_TABLE+" set group_id=? where id=?";
        this.jdbcTemplate.update(sql, groupId, ua.getId());
    }

    @Override
    public void deleteWitnessNote(Long noteId) {
        String sql = "select note_id from "+DATA_TABLE+" where id=?";
        Long id = this.jdbcTemplate.queryForLong(sql, noteId);
        sql = "delete from "+DATA_TABLE+" where id=?";
        this.jdbcTemplate.update(sql, noteId);
        
        sql = "select count(*) as cnt from "+DATA_TABLE+" where note_id=?";
        int cnt = this.jdbcTemplate.queryForInt(sql,id);
        if ( cnt == 0 ) {
            sql = "delete from "+MAIN_TABLE+" where id=?";
            this.jdbcTemplate.update(sql, id);
        }
    }
    
    @Override
    public void addWitnessNote(UserAnnotation ua, Long witId, String text) {
        StringBuilder sql = new StringBuilder("select id from  ");
        sql.append(MAIN_TABLE);
        sql.append(" where set_id=? and base_id=?");        
        sql.append(" and range_start=? and range_end=? and group_id is null ");
        Long noteId = this.jdbcTemplate.queryForLong(sql.toString(), 
            ua.getSetId(), ua.getBaseId(), ua.getBaseRange().getStart(), ua.getBaseRange().getEnd());
        
        String s = "insert into "+DATA_TABLE+" (note_id,witness_id,note) values (?,?,?)";
        this.jdbcTemplate.update(s, noteId, witId, text);
    }
    
    @Override
    public void updateWitnessNote(Long noteId, String text) {
        String sql = 
            "update juxta_user_note_data set note=? where id=?";
        this.jdbcTemplate.update(sql, text, noteId);
    }
    
    @Override
    public void updateGroupNote(Long groupId, String newNote) {
        String sql = 
            "update juxta_user_note_data set note=? where note_id in (select id from juxta_user_note where group_id=?) ";
        this.jdbcTemplate.update(sql, newNote, groupId);
    }
    
    @Override
    public void deleteGroupNote(ComparisonSet set, Long groupId) {
        final String sql = "delete from "+MAIN_TABLE+" where set_id=? and group_id=?";
        this.jdbcTemplate.update(sql, set.getId(), groupId );
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
                Long id = rs.getLong("n_id");
                UserAnnotation ua = map.get(id);
                if ( ua == null ) {
                    ua = new UserAnnotation();
                    ua.setId(id);
                    ua.setBaseId( rs.getLong("base_id") );
                    ua.setSetId( rs.getLong("set_id") );
                    Object gid = rs.getObject("group_id");
                    if ( gid != null ) {
                        ua.setGroupId(rs.getLong("group_id"));
                    }
                    ua.setBaseRange( new Range(
                        rs.getLong("range_start"),
                        rs.getLong("range_end") ) );
                    map.put(id, ua);
                }
                UserAnnotation.Data note = new UserAnnotation.Data(rs.getLong("witness_id"), rs.getString("note"));
                note.setId(rs.getLong("d_id"));
                note.setGroup(rs.getBoolean("is_group"));
                ua.addNote(note);
            }
            List<UserAnnotation> out = new ArrayList<UserAnnotation>(map.values());
            Collections.sort(out);
            return out;
        }
    }

}
