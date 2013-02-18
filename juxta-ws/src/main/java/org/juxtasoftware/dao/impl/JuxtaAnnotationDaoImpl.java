package org.juxtasoftware.dao.impl;

import java.io.IOException;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;

import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Range;
import eu.interedition.text.mem.SimpleName;
import eu.interedition.text.rdbms.RelationalName;
import eu.interedition.text.rdbms.RelationalText;
import eu.interedition.text.rdbms.RelationalTextRepository;

@Repository
public class JuxtaAnnotationDaoImpl implements JuxtaAnnotationDao, InitializingBean {
    private final String tableName = "juxta_annotation";
    private SimpleJdbcInsert insert;
    @Autowired private JdbcTemplate jdbcTemplate;
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(jdbcTemplate).withTableName(tableName).usingGeneratedKeyColumns("id");
    }
    
    @Override
    public int create( final List<JuxtaAnnotation> annotations) {
        if ( annotations.isEmpty() ) {
            return 0;
        }
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ").append(tableName);
        sql.append(" (set_id, witness_id, text_id, qname_id, range_start, range_end, manual)");
        sql.append(" values (?,?,?,?,?,?,?)");
        int[] rowsAffected = this.jdbcTemplate.batchUpdate(sql.toString(), new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, annotations.get(i).getSetId());
                    ps.setLong(2, annotations.get(i).getWitnessId());
                    ps.setLong(3, ((RelationalText)annotations.get(i).getText()).getId());
                    ps.setLong(4, ((RelationalName)annotations.get(i).getName()).getId());
                    ps.setLong(5, annotations.get(i).getRange().getStart());
                    ps.setLong(6, annotations.get(i).getRange().getEnd());
                    ps.setBoolean(7, annotations.get(i).isManual());
                }

                @Override
                public int getBatchSize() {
                    return annotations.size();
                }
                
            } );
        return rowsAffected.length;
    }
    
    @Override
    public Long create(JuxtaAnnotation annotation) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("set_id", annotation.getSetId());
        ps.addValue("witness_id", annotation.getWitnessId());
        ps.addValue("text_id", ((RelationalText)annotation.getText()).getId());
        ps.addValue("qname_id", ((RelationalName)annotation.getName()).getId());
        ps.addValue("range_start", annotation.getRange().getStart());
        ps.addValue("range_end", annotation.getRange().getEnd());
        ps.addValue("manual", annotation.isManual() );
        return (Long)this.insert.executeAndReturnKey( ps );
    }
    
    @Override
    public void delete(JuxtaAnnotation annotation) {
        final String sql = "delete from " + this.tableName + " where id=?";
        this.jdbcTemplate.update(sql, annotation.getId());
    }

    @Override
    public long findNextTokenStart(final Long witnessId, final long fromPos) {
        final String sql = 
            "select range_start from juxta_annotation" +
            " where witness_id=? and range_start > ?  order by range_start asc limit 1";
        try {
            long pos = this.jdbcTemplate.queryForLong(sql, witnessId, fromPos);
            return pos;
        } catch (Exception e) {
            return -1;
        }
    }
    
    @Override
    public long findPriorTokenEnd(final Long witnessId, final long fromPos) {
        final String sql = 
            "select range_end from juxta_annotation" +
            " where witness_id=? and range_end < ? order by range_end desc limit 1";
        try {
            return this.jdbcTemplate.queryForLong(sql, witnessId, fromPos);
        } catch (Exception e) {
            return fromPos;
        }
    }

    @Override
    public JuxtaAnnotation find(Long id, boolean includeText) {
        StringBuilder sql = new StringBuilder( getSql() );
        sql.append(" where a.id = ?");
        List<JuxtaAnnotation> annotations = this.jdbcTemplate.query( sql.toString(), new AnnotationMapper(), id);
        Long textId = null;
        if ( annotations.size() > 0 ) {
            textId = ((RelationalText)annotations.get(0).getText()).getId();
        }
        if ( includeText ) {
            readTokenContent(textId, annotations);
        }
        return DataAccessUtils.uniqueResult( annotations );
    }

    @Override
    public List<JuxtaAnnotation> list( final AnnotationConstraint constraint) {
        StringBuilder sql = new StringBuilder( getSql() );
        sql.append(" where");
        sql.append(" t.id = ? and a.set_id = ?");
        List<JuxtaAnnotation> annotations = null;
        
        if ( constraint.getFilter() != null ) {
            sql.append(" and q.id in (");
            sql.append( constraint.getFilter().getQNameIdListAsString() );
            sql.append(")");
        } 
        
        if ( constraint.getRanges().size() > 0 ) {
            if ( constraint.getRanges().size() > 1 ) {
                sql.append(" and (");
            } else {
                sql.append(" and ");
            }
            int cnt = 0;
            for ( Range r : constraint.getRanges() ) {
                if ( cnt > 0 ) {
                    sql.append(" or");
                }
                sql.append(" a.range_start >= ").append(r.getStart()).append(" and a.range_end <= ").append(r.getEnd());
                cnt++;
            }
            if ( constraint.getRanges().size() > 1 ) {
                sql.append(") order by a.range_start asc");
            } else {
                sql.append(" order by a.range_start asc");
            }

            annotations = this.jdbcTemplate.query(sql.toString(), 
                new AnnotationMapper(), 
                constraint.getTextId(), constraint.getSetId() );
        } else {

            sql.append(" order by a.range_start asc");
            
            annotations = this.jdbcTemplate.query(sql.toString(), 
                new AnnotationMapper(), 
                constraint.getTextId(), constraint.getSetId() );
        }
        
        // pull token content for all from the witness text
        if ( constraint.isIncludeText() ) {
            readTokenContent( constraint.getTextId(), annotations );
        }
        
        return annotations;
    }
    
    private void readTokenContent(Long textId, List<JuxtaAnnotation> annotations) {
        try {
            if ( annotations.size() == 0) {
                return;
            }

            // get a reader for the text content associated with the annotations
            final String sql = "select content from text_content where id=?";
            Reader txtReader = DataAccessUtils.uniqueResult(this.jdbcTemplate.query(sql, new RowMapper<Reader>() {
                @Override
                public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return rs.getCharacterStream("content");
                }
            }, textId));
            
            // pick the first non-gap annotation
            Iterator<JuxtaAnnotation> itr = annotations.iterator();
            JuxtaAnnotation currAnno = null;
            while (itr.hasNext()) {
                JuxtaAnnotation a = itr.next();
                if ( a.getRange().length() > 0 ) {
                    currAnno= a;
                    break;
                }
            }
            if ( currAnno == null) {
                // all gaps? nothin to do
                return;
            }
            
            // fill in the content for all non-gap annotations
            int pos = 0;
            StringBuilder currToken = new StringBuilder();
            while (true ) {
                int data = txtReader.read();
                if ( data == -1 ) {
                    currAnno.setContent( currToken.toString() );
                    break;
                } else {
                    if ( pos >= currAnno.getRange().getStart() && pos < currAnno.getRange().getEnd()) {
                        currToken.append( (char)data );
                        if ( (pos+1) == currAnno.getRange().getEnd() ) {
                            //System.err.println("["+currToken.toString()+"] len " +currToken.length());
                            currAnno.setContent( currToken.toString() );
                            currAnno = null;
                            while ( itr.hasNext() ) {
                                JuxtaAnnotation test = itr.next();
                                if ( test.getRange().length() > 0) {
                                    currAnno = test;
                                    currToken = new StringBuilder();
                                    break;
                                }
                            }
                            if ( currAnno == null ) {
                                break;
                            }
                        }
                    }
                    pos++;
                }
            }
        } catch (IOException e ) {
            throw new DataAccessResourceFailureException("Unable to retrieve annotation content", e);
        }
    }
    
    private String getSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("select a.id, a.set_id, a.witness_id, a.text_id, a.qname_id, a.range_start, a.range_end, ");
        sql.append(RelationalTextRepository.selectTextFrom("t"));
        sql.append(", q.id, q.local_name, q.namespace ");
        sql.append(" from ").append(this.tableName).append(" as a");
        sql.append(" join text_content t on a.text_id = t.id");
        sql.append(" join text_qname q on a.qname_id = q.id");
        
        return sql.toString();
    }
    
    /**
     * Map annotation db to model
     * @author loufoster
     */
    private static class AnnotationMapper implements RowMapper<JuxtaAnnotation> {
        public JuxtaAnnotation mapRow(ResultSet rs, int rowNum) throws SQLException {
            final JuxtaAnnotation annotation = new JuxtaAnnotation(
                rs.getLong("id"),
                rs.getLong("set_id"),
                rs.getLong("witness_id"),
                RelationalTextRepository.mapTextFrom(rs, "t"),
                new SimpleName(rs.getString("namespace"), rs.getString("local_name")),
                new Range(rs.getInt("range_start"), rs.getInt("range_end"))
            );
            return annotation;
        }
    }
}
