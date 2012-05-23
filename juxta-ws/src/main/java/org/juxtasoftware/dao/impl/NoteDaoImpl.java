package org.juxtasoftware.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.NoteDao;
import org.juxtasoftware.model.Note;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Range;

@Repository
public class NoteDaoImpl implements NoteDao {

    private static final String TABLE_NAME = "juxta_note";
    @Autowired private JdbcTemplate jdbcTemplate;
    
    @Override
    public boolean hasNotes(final Long witnessId) {
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
    public void create(final List<Note> notes) {
        if ( notes.isEmpty() ) {
            return;
        }
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ").append(TABLE_NAME);
        sql.append(" (witness_id, note_type, target_xml_id,");
        sql.append("  anchor_start, anchor_end, content)");
        sql.append(" values (?,?,?,?,?,?)");
        this.jdbcTemplate.batchUpdate(sql.toString(), new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, notes.get(i).getWitnessId());
                    ps.setString(2, notes.get(i).getType());
                    ps.setString(3, notes.get(i).getTargetID());
                    ps.setLong(4, notes.get(i).getAnchorRange().getStart());
                    ps.setLong(5, notes.get(i).getAnchorRange().getEnd() );
                    ps.setString(6, notes.get(i).getContent());
                }

                @Override
                public int getBatchSize() {
                    return notes.size();
                }
                
            } );
    }
    
    @Override
    public void deleteAll(Long witnessId) {
        final String sql = "delete from "+TABLE_NAME+" where witness_id = ?";
        this.jdbcTemplate.update(sql, witnessId);
    }

    @Override
    public List<Note> find(final Long witnessId ) {
        final StringBuilder sql = basicSelectSql();
        sql.append(" where jn.witness_id = ? order by jn.anchor_start, jn.anchor_end").toString();
        return this.jdbcTemplate.query(sql.toString(), ROW_MAPPER, witnessId);
    }

    protected StringBuilder basicSelectSql () {
        StringBuilder sb = new StringBuilder();
        sb.append("select jn.id as jn_id, jn.witness_id as jn_witness_id, ");
        sb.append("       jn.note_type as jn_note_type, jn.target_xml_id as jn_target_xml_id, "); 
        sb.append("       jn.anchor_start as jn_anchor_start, jn.anchor_end as jn_anchor_end, ");
        sb.append("       jn.content as jn_content ");
        sb.append("from juxta_note jn ");
        return sb;
    }

    private static final RowMapper<Note> ROW_MAPPER = new RowMapper<Note>() {
        @Override
        public Note mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Note note = new Note();
            note.setId(rs.getLong("jn_id"));
            note.setWitnessId(rs.getLong("jn_witness_id"));
            note.setType(rs.getString("jn_note_type"));
            note.setTargetID(rs.getString("jn_target_xml_id"));
            note.setAnchorRange(new Range(rs.getLong("jn_anchor_start"), rs.getLong("jn_anchor_end")));
            note.setContent(rs.getString("jn_content"));
            return note;
        }
    };
}
