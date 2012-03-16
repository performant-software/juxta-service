package org.juxtasoftware.dao.impl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.juxtasoftware.dao.RevisionDao;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Revision;
import org.juxtasoftware.model.RevisionSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.QNameFilters;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Range;
import eu.interedition.text.rdbms.RelationalText;

@Repository
public class RevisionDaoImpl implements RevisionDao, InitializingBean {
    private static final String TABLE_NAME = "juxta_revision_set";
    private SimpleJdbcInsert insert;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private QNameFilters filters;
    

    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate).
            withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
    }
    
    @Override
    public Long createRevisionSet( final RevisionSet revSet ) {

        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", revSet.getName());
        ps.addValue("source_id", revSet.getSourceId());
        final Long id = this.insert.executeAndReturnKey(ps).longValue();
        revSet.setId(id);
            
        insertIndexes(revSet);
        
        return id;
    }

    private void insertIndexes(final RevisionSet revSet) {
        // NOTE: revNumbers is a list of revision sequence numbers that were
        // accepted. These numbers are ZERO BASED!!!
        // for example; there are 10 revisions in a doc, ordered by document offset.
        // if the 1st and 3rd are to be accepted, revNumbers = [0,2].
        this.jdbcTemplate.batchUpdate(
            "insert into juxta_revision_set_index (set_id, revision_index) values (?,?)",
            new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, revSet.getId() );
                    ps.setLong(2, revSet.getRevisionIndexes().get(i) );
                }

                @Override
                public int getBatchSize() {
                    return revSet.getRevisionIndexes().size();
                }
                
            } );
    }
    
    @Override
    public boolean hasRevisionSets(final Long sourceId) {
        final StringBuilder sql = new StringBuilder("select count(*) as cnt from ");
        sql.append(TABLE_NAME).append(" where source_id = ?");
        int cnt = this.jdbcTemplate.queryForInt(sql.toString(), sourceId);
        return (cnt > 0);
    }
    
    @Override
    public List<RevisionSet> listRevisionSets(final Long sourceId) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select id, name, source_id from ").append(TABLE_NAME);
        sql.append(" where source_id=?");
        List<RevisionSet> sets = this.jdbcTemplate.query(sql.toString(), new RevisionSetMapper(), sourceId);
        
        // now get the accepted sequence for each
        for ( RevisionSet set:sets) {
            final StringBuilder sql2 = new StringBuilder();
            sql2.append("select revision_index from juxta_revision_set_index");
            sql2.append(" where set_id=? order by revision_index asc");
            set.setRevisionIndexes( this.jdbcTemplate.queryForList(sql2.toString(), Integer.class, set.getId()));
        }
        return sets;
    }
    
    @Override
    public void deleteSourceRevisionSets(final Long sourceId) {
        final String sql = "delete from "+TABLE_NAME+" where source_id=?";
        this.jdbcTemplate.update(sql, sourceId);
    }
    
    @Override
    public void deleteRevisionSet( final Long revisonSetId) {
        final String sql = "delete from "+TABLE_NAME+" where id=?";
        this.jdbcTemplate.update(sql, revisonSetId);
    }
    
    @Override
    public void updateRevisionSet( final RevisionSet rs) {
        // update the main data
        final String sql = "update "+TABLE_NAME+" set name=? where id=?";
        this.jdbcTemplate.update(sql, rs.getName(), rs.getId());
        
        // purge and replace the index table
        final String sql2="delete from juxta_revision_set_index where set_id=?";
        this.jdbcTemplate.update(sql2, rs.getId());
        insertIndexes(rs);
    }
    
    @Override
    public boolean hasRevisions(Witness witness) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select count(*) as cnt from ").append(TABLE_NAME);
        sql.append(" where source_id = ?");
        int cnt = this.jdbcTemplate.queryForInt(sql.toString(), witness.getSourceId());
        return (cnt > 0);
    }
    
    @Override
    public RevisionSet getRevsionSet( final Long id ) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select id, name, source_id from ").append(TABLE_NAME);
        sql.append(" where id=?");
        RevisionSet set = DataAccessUtils.uniqueResult( 
            this.jdbcTemplate.query(sql.toString(), new RevisionSetMapper(), id));
        
        // now get the accepted sequence
        final StringBuilder sql2 = new StringBuilder();
        sql2.append("select revision_index from juxta_revision_set_index");
        sql2.append(" where set_id=? order by revision_index asc");
        set.setRevisionIndexes( this.jdbcTemplate.queryForList(sql2.toString(), Integer.class, id));
        return set;
    }

    @Override
    public List<Revision> getRevisions(final Witness witness) {
        final QNameFilter revFilter = this.filters.getRevisionsFilter();
        final Long textId = ((RelationalText)witness.getText()).getId();
        final List<Integer> revs;
        if ( witness.getRevisionSetId() != null ) {
           revs = getRevsionSet(witness.getRevisionSetId()).getRevisionIndexes();
        } else {
            revs = new ArrayList<Integer>();
        }
        final StringBuilder sql = new StringBuilder();
        
        sql.append("select ta.id as ta_id, qn.local_name as qn_local_name,");
        sql.append(" ta.range_start as ta_range_start, ta.range_end as ta_range_end");
        sql.append(" from text_annotation as ta");
        sql.append(" join text_qname as qn on qn.id = ta.name");
        sql.append(" where ta.text = ? and ta.name in ( ");
        sql.append( revFilter.getQNameIdListAsString());
        sql.append(" ) order by ta.range_start asc");
        
        return this.jdbcTemplate.query(sql.toString(), new RowMapper<Revision>() {

            @Override
            public Revision mapRow(ResultSet rs, int rowNum) throws SQLException {
                Revision rev = new Revision();
                rev.setWitnessId(witness.getId());
                rev.setAccepted( revs.contains(rowNum));
                rev.setAnnotationId( rs.getLong("ta_id"));
                rev.setRange( new Range(rs.getLong("ta_range_start"), rs.getLong("ta_range_end")));
                rev.setType( rs.getString("qn_local_name"));
            
                return rev;
            }
            
        }, textId);
    }
    
    private static class RevisionSetMapper implements RowMapper<RevisionSet> {
        @Override
        public RevisionSet mapRow(ResultSet rs, int rowNum) throws SQLException {
            RevisionSet set = new RevisionSet();
            set.setId( rs.getLong("id"));
            set.setName( rs.getString("name"));
            set.setSourceId( rs.getLong("source_id"));
            return set;
        }
    }
}
