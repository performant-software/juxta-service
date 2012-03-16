package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.JuxtaAnnotationDao;
import org.juxtasoftware.model.AnnotationConstraint;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.google.common.collect.Iterables;

import eu.interedition.text.Annotation;
import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Range;
import eu.interedition.text.mem.SimpleName;
import eu.interedition.text.rdbms.RelationalAnnotation;
import eu.interedition.text.rdbms.RelationalTextRepository;

@Repository
public class JuxtaAnnotationDaoImpl implements JuxtaAnnotationDao {
    private final String tableName = "text_annotation";
    @Autowired private AnnotationRepository annotationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    
    @Override
    public Long create(JuxtaAnnotation annotation) {
        Annotation created = Iterables.getFirst(annotationRepository.create(annotation), null);
        return new Long(((RelationalAnnotation)created).getId());
    }
    
    @Override
    public void delete(JuxtaAnnotation annotation) {
        final String sql = "delete from " + this.tableName + " where id=?";
        this.jdbcTemplate.update(sql, annotation.getId());
    }
    
    @Override
    public long findNextTokenStart(final long textId, final long fromPos) {
        final String sql = 
            "select range_start from text_annotation" +
            " where text=? and range_start > ?  order by range_start asc limit 1";
        try {
            return this.jdbcTemplate.queryForLong(sql, textId, fromPos);
        } catch (Exception e) {
            return fromPos;
        }
    }

    @Override
    public JuxtaAnnotation find(Long id, boolean includeText) {
        String textSql = RelationalTextRepository.selectTextFrom("t");
        if ( includeText ) {
            textSql += ", t.content as t_content";
        }
        final StringBuilder sql = new StringBuilder();
        sql.append("select a.id, a.text, a.name, a.range_start, a.range_end, ");
        sql.append(textSql); 
        sql.append(", q.local_name, q.namespace");
        sql.append(", w.id as witness_id");
        sql.append(" from " + this.tableName + " as a ");
        sql.append(" join text_content t on a.text = t.id");
        sql.append(" join text_qname q on a.name = q.id");
        sql.append(" join juxta_witness w on w.text_id = t.id");
        sql.append(" where a.id = ?");
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query( sql.toString(), new AnnotationMapper(includeText), id));
    }

    @Override
    public List<JuxtaAnnotation> list( final AnnotationConstraint constraint) {
        
        // build the monster
        StringBuilder sql = new StringBuilder();
        sql.append("select w.id as witness_id, a.id, a.text, a.name, a.range_start, a.range_end, ");
        sql.append(RelationalTextRepository.selectTextFrom("t"));
        if ( constraint.isIncludeText() ) {
            sql.append(", t.content as t_content ");
        }
        sql.append(", q.id, q.local_name, q.namespace ");
        sql.append(" from ").append(this.tableName).append(" as a");
        sql.append(" join text_content t on a.text = t.id");
        sql.append(" join text_qname q on a.name = q.id");
        sql.append(" join juxta_witness w on w.text_id = t.id");
        sql.append(" where");
        sql.append(" t.id = ?");
        
        if ( constraint.getFilter() != null ) {
            sql.append(" and q.id in (");
            sql.append( constraint.getFilter().getQNameIdListAsString() );
            sql.append(")");
        } 
        
        if ( constraint.getRange() != null ) {
            sql.append(" and a.range_start >= ? and a.range_end <= ?");
            sql.append(" order by a.range_start asc");
            if ( constraint.getLimit() != null ) {
                sql.append(" limit ").append(constraint.getLimit());
            }

            return this.jdbcTemplate.query(sql.toString(), 
                new AnnotationMapper(constraint.isIncludeText()), 
                constraint.getTextId(), 
                constraint.getRange().getStart(),
                constraint.getRange().getEnd() );
        } else {

            sql.append(" order by a.range_start asc");
            if ( constraint.getLimit() != null ) {
                sql.append(" limit ").append(constraint.getLimit());
            }
            
            return this.jdbcTemplate.query(sql.toString(), 
                new AnnotationMapper(constraint.isIncludeText()), 
                constraint.getTextId() );
        }
    }
    
    /**
     * Map annotation db to model
     * @author loufoster
     */
    private static class AnnotationMapper implements RowMapper<JuxtaAnnotation> {
        private boolean includeContent;
        public AnnotationMapper( boolean includeContent ) {
            this.includeContent = includeContent;
        }
        public JuxtaAnnotation mapRow(ResultSet rs, int rowNum) throws SQLException {
            final JuxtaAnnotation annotation = new JuxtaAnnotation(
                rs.getLong("witness_id"),
                RelationalTextRepository.mapTextFrom(rs, "t"),
                new SimpleName(rs.getString("namespace"), rs.getString("local_name")),
                new Range(rs.getInt("range_start"), rs.getInt("range_end")),
                null,
                rs.getInt("id"));
            if ( this.includeContent ) {
                annotation.setContent( rs.getString("t_content")
                    .substring( (int)annotation.getRange().getStart(), (int)annotation.getRange().getEnd())
                    .trim() );
            }
            return annotation;
        }
    }
}
