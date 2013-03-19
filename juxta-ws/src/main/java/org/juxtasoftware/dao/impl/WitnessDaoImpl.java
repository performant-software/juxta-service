package org.juxtasoftware.dao.impl;

import static eu.interedition.text.rdbms.RelationalTextRepository.selectTextFrom;

import java.io.IOException;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.lucene.index.CorruptIndexException;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.ComparisonSet.Status;
import org.juxtasoftware.model.ResourceInfo;
import org.juxtasoftware.model.RevisionInfo;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.util.LuceneHelper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.TextRepository;
import eu.interedition.text.rdbms.RelationalText;
import eu.interedition.text.rdbms.RelationalTextRepository;

/**
 * DAO implementation for juxta witness CRUD
 * 
 * @author loufoster
 *
 */
@Repository
public class WitnessDaoImpl implements WitnessDao, InitializingBean {

    protected SimpleJdbcInsert insert;
    private String tableName;
    @Autowired ComparisonSetDao setDao;
    @Autowired TextRepository textRepository;
    @Autowired @Qualifier("executor") private TaskExecutor taskExecutor;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LuceneHelper lucene;
    
    public WitnessDaoImpl() {
        this.tableName = "juxta_witness";
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(this.jdbcTemplate).withTableName(tableName).usingGeneratedKeyColumns("id");
    }

    @Override
    public Long create(Witness w) throws CorruptIndexException, IOException {
        Long id =  insert.executeAndReturnKey(toInsertData(w)).longValue();

        // add the new witness to the lucene index
        String ws = getWorkspaceName(w.getWorkspaceId());
        Long textId = ((RelationalText)w.getText()).getId();
        Reader r = getContentStream(w);
        this.lucene.addDocument("witness", ws, id, w.getName(), textId, r);

        return id;
    }
    
    @Override
    public ResourceInfo getInfo( final Long witnessId ) {
        final String sql = 
            "select w.id as id, w.name as name,w.created as created,w.updated as updated,ws.name as workspace " +
            " from juxta_witness w inner join juxta_workspace ws on workspace_id = ws.id where w.id=?";
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql, new RowMapper<ResourceInfo>(){

            @Override
            public ResourceInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new ResourceInfo(rs.getLong("id"), rs.getString("workspace"), rs.getString("name"), rs.getTimestamp("created"), rs.getTimestamp("updated"));
            }}, witnessId));
    }
    
    @Override
    public void rename(final Witness witness, final String newName) {
        final String sql = "update "+this.tableName+" set name = ? where id = ?";
        this.jdbcTemplate.update(sql, newName, witness.getId());
    }
    
    @Override
    public void updateContent( Witness witness, final Text newContent) throws CorruptIndexException, IOException {
              
        Text oldTxt = witness.getText();
        Long oldTxtId = ((RelationalText)witness.getText()).getId() ;
        Long newTxtId = ((RelationalText)newContent).getId() ;
        String sql = "update "+this.tableName+" set text_id=?, updated=? where id=?";
        this.jdbcTemplate.update(sql, newTxtId,  new Date(), witness.getId() );
        this.textRepository.delete( oldTxt );
        
        witness.setText(newContent);
        
        // Update the index: remove the old and add the updted src as new
        String ws = getWorkspaceName(witness.getWorkspaceId());
        this.lucene.deleteDocument( oldTxtId );
        this.lucene.addDocument("witness", ws, witness.getId(), witness.getName(), newTxtId, getContentStream(witness) );
    }
    
    private String getWorkspaceName(Long id) {
        String sql = "select name from juxta_workspace where id=?";
        return this.jdbcTemplate.queryForObject(sql, String.class, id);
    }
    
    @Override
    public Reader getContentStream(Witness witness) {
        final String sql = "select content from text_content where id=?";
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql, new RowMapper<Reader>(){
            @Override
            public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getCharacterStream("content");
            }}, ((RelationalText)witness.getText()).getId() ) );
    }
    
    @Override
    public List<Witness> list( final Workspace ws) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" where workspace_id=? order by created desc, updated desc");
        return jdbcTemplate.query(sql.toString(), new WitnessMapper(), ws.getId());
    }
    
    @Override
    public void delete(final Witness witness) {
        // kill it from index
        this.lucene.deleteDocument( ((RelationalText)witness.getText()).getId() );
        
        // get a list of all uses of this witness.
        // Mark sets as NOT collated, clear their collation cache and remove all
        // alignments
        final List<Usage> usage = getUsage(witness);
        final Text delText = witness.getText();

        // immediately delete the witness. This is quick, but orphans
        // a bunch of data - data that can take a long time to delete.
        // Push this extra deletion into a worker thread.
        this.jdbcTemplate.update("delete from " + this.tableName + " where id = ?", witness.getId());
        final List<ComparisonSet> sets = new ArrayList<ComparisonSet>();
        for (Usage u : usage) {
            if (u.getType().equals(Usage.Type.COMPARISON_SET)) {
                ComparisonSet set = setDao.find(u.getId());
                if ( !(set.getStatus().equals(Status.DELETED) || !set.getStatus().equals(Status.NOT_COLLATED)) ) {
                    set.setStatus(Status.NOT_COLLATED);
                    this.setDao.update(set);
                }
                sets.add(set);
            }
        }

        this.taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                WitnessDaoImpl.this.textRepository.delete(delText);
                for (ComparisonSet set : sets) {
                    setDao.clearCollationData(set);
                }
            }
        });
    }

    protected SqlParameterSource toInsertData(Witness object) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", object.getName());
        ps.addValue("source_id", object.getSourceId());
        ps.addValue("xslt_id", object.getXsltId());
        ps.addValue("text_id", ((RelationalText) object.getText()).getId());
        ps.addValue("workspace_id", object.getWorkspaceId());
        ps.addValue("created", new Date());
        return ps;
    }
    
    @Override
    public List<Witness> find(ComparisonSet set) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" join juxta_comparison_set_member csm on csm.witness_id = w.id");
        sql.append(" where csm.set_id = ? order by w_created desc");
        return new ArrayList<Witness>( 
            this.jdbcTemplate.query(sql.toString(), new WitnessMapper(), set.getId()));
    }
    
    @Override
    public Witness find(Workspace ws, String title) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" where w.workspace_id = ? and w.name = ?");
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql.toString(), 
            new WitnessMapper(), ws.getId(), title));
    }
    
    @Override
    public boolean exists( final Workspace ws, final String title) {
        final String sql = "select count(*) as cnt from " +this.tableName+ " where workspace_id=? and name=?";
        int cnt = this.jdbcTemplate.queryForInt(sql, ws.getId(), title);
        return ( cnt > 0);
    }
    
    @Override
    public Witness find(ComparisonSet set, String title) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" join juxta_comparison_set_member csm on csm.witness_id = w.id");
        sql.append(" where csm.set_id = ? and w.name = ?");
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql.toString(), 
            new WitnessMapper(), set.getId(), title));
    }
    
    private StringBuilder getSqlBuilder() {
        final StringBuilder sql = new StringBuilder();
        sql.append("select w.id as w_id, w.name as w_name, w.created as w_created, w.updated as w_updated, ");
        sql.append("       w.source_id as w_source_id, w.xslt_id as w_xslt_id,");
        sql.append("       w.workspace_id as w_workspace_id, ");
        sql.append( selectTextFrom("wt"));
        sql.append(" from juxta_witness w");
        sql.append(" join text_content wt on w.text_id = wt.id");
        return sql;
    }

    @Override
    public Witness find(Long id) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" where w.id = ?");
        return DataAccessUtils.uniqueResult(
                this.jdbcTemplate.query(sql.toString(), new WitnessMapper(), id));
    }
    
    @Override
    public void clearRevisions(Witness witness) {
        final String sql = "delete from juxta_revision where witness_id=?";
        this.jdbcTemplate.update(sql, witness.getId());
    }
    
    @Override
    public void addRevisions(final List<RevisionInfo> revs) {
        if ( revs.size() == 0 ) {
            return;
        }
        StringBuilder sql = new StringBuilder();
        sql.append("insert into juxta_revision");
        sql.append(" (witness_id, revision_type, start, end, is_included, content)");
        sql.append(" values (?,?,?,?,?,?)");
        this.jdbcTemplate.batchUpdate(sql.toString(), new BatchPreparedStatementSetter() {

                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, revs.get(i).getWitnessId() );
                    ps.setString(2, revs.get(i).getType().toString());
                    ps.setLong(3, revs.get(i).getRange().getStart() );
                    ps.setLong(4, revs.get(i).getRange().getEnd());
                    ps.setBoolean(5, revs.get(i).isIncluded());
                    ps.setString(6, revs.get(i).getText());
                }

                @Override
                public int getBatchSize() {
                    return revs.size();
                }
                
            } );
    }
    
    @Override
    public boolean hasRevisions(final Witness witness) {
        final String sql ="select count(*) as cnt from juxta_revision where witness_id=?"; 
        int cnt = this.jdbcTemplate.queryForInt(sql, witness.getId());
        return ( cnt > 0);
    }
    
    @Override
    public List<RevisionInfo> getRevisions(final Witness witness) {
        StringBuilder sql = new StringBuilder();
        sql.append("select id,witness_id,revision_type,start,end,content,is_included"); 
        sql.append(" from juxta_revision where witness_id=?");
        return this.jdbcTemplate.query(sql.toString(), new RowMapper<RevisionInfo>(){

            @Override
            public RevisionInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
                Range rng = new Range( rs.getLong("start"), rs.getLong("end"));
                RevisionInfo.Type type = RevisionInfo.Type.valueOf( rs.getString("revision_type"));
                RevisionInfo inf = new RevisionInfo( rs.getLong("id"), rs.getLong("witness_id"),
                    type, rng, rs.getString("content"), rs.getBoolean("is_included") );
                return inf;
            }}, witness.getId());
    }
    
    @Override
    public List<Usage> getUsage(Witness witness) {
        String setSql = "select distinct set_id,name from juxta_comparison_set_member " +
            "inner join juxta_comparison_set on juxta_comparison_set.id = set_id " +
            "where witness_id =?";
        List<Usage> usage = this.jdbcTemplate.query(setSql, new RowMapper<Usage>(){
            @Override
            public Usage mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Usage(Usage.Type.COMPARISON_SET, rs.getLong("set_id"), rs.getString("name"));
            }
            
        },witness.getId());
        
        // include the source (that we already know) that was used to generate this witness
        String nmSql = "select name from juxta_source where id=?";
        String srcName = this.jdbcTemplate.queryForObject(nmSql, String.class, witness.getSourceId());
        usage.add( new Usage(Usage.Type.SOURCE, witness.getSourceId(), srcName));
        return usage;
    }
    
    /**
     * Map witness result set into model
     * @author loufoster
     */
    private static class WitnessMapper implements RowMapper<Witness> {

        @Override
        public Witness mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Witness witness = new Witness();
            witness.setId(rs.getLong("w_id"));
            witness.setName(rs.getString("w_name"));
            witness.setSourceId( rs.getLong("w_source_id"));
            Object templateIdObj = rs.getObject("w_xslt_id");
            if (templateIdObj != null ) {
                witness.setXsltId( (Long)templateIdObj );
            }
            witness.setWorkspaceId( rs.getLong("w_workspace_id") );
            witness.setCreated( rs.getTimestamp("w_created") );
            witness.setUpdated( rs.getTimestamp("w_updated") );

            witness.setText( RelationalTextRepository.mapTextFrom(rs, "wt") );
            return witness;
        }
    }
}
