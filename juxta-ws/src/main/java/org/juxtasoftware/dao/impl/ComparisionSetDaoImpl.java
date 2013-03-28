package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.CollatorConfig.HyphenationFilter;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.ComparisonSet.Status;
import org.juxtasoftware.model.ResourceInfo;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
@Repository
public class ComparisionSetDaoImpl extends JuxtaDaoImpl<ComparisonSet> implements ComparisonSetDao {

    @Autowired private WitnessDao witnessDao;
    @Autowired private CacheDaoImpl cacheDao;
    @Autowired @Qualifier("executor") private TaskExecutor taskExecutor;
    
    private SimpleJdbcInsert memberInsert;
    protected SimpleJdbcInsert configInsert;
    protected SimpleJdbcInsert noteInsert;
    private static final SetRowMapper SET_ROW_MAPPER = new SetRowMapper();
    private static final CollatorConfigRowMapper CFG_ROW_MAPPER = new CollatorConfigRowMapper();
    private static final String CFG_TABLE = "juxta_collator_config";
    private static final String SET_MEMBER_TABLE = "juxta_comparison_set_member";
    private static final String NOTE_TABLE = "juxta_comparison_note";

    public ComparisionSetDaoImpl() {
        super("juxta_comparison_set");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.memberInsert = new SimpleJdbcInsert(jt).withTableName( SET_MEMBER_TABLE );
        this.configInsert = new SimpleJdbcInsert(jt).withTableName( CFG_TABLE );
        this.noteInsert = new SimpleJdbcInsert(jt).withTableName( NOTE_TABLE );
    }
    
    @Override
    public long create(ComparisonSet set) {
        Long id = super.create(set);
        set.setId( id );
        CollatorConfig cfg = new CollatorConfig();
        createCollatorConfig(id, cfg);
        return id;
    }
    
    @Override
    public ResourceInfo getInfo( final Long setId ) {
        final String sql = 
            "select w.id as id, w.name as name,w.created as created,w.updated as updated,ws.name as workspace " +
            " from juxta_comparison_set w inner join juxta_workspace ws on workspace_id = ws.id where w.id=?";
        return DataAccessUtils.uniqueResult( this.jt.query(sql, new RowMapper<ResourceInfo>(){

            @Override
            public ResourceInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new ResourceInfo(rs.getLong("id"), rs.getString("workspace"), rs.getString("name"), rs.getTimestamp("created"), rs.getTimestamp("updated"));
            }}, setId));
    }
    
    @Override
    public void clearCollationData( ComparisonSet set) {
        // be sure the set is always marked as not collated (unless its been deleted)
        if ( set.getStatus().equals(Status.DELETED) == false ) {
            set.setStatus(ComparisonSet.Status.NOT_COLLATED);
            update(set);
        }
  
        // clear alignments, annotations and cached visualization data
        this.cacheDao.deleteAll(set.getId());

        while ( true ) {
            // NOTE: the alignments will cascade delete with the annotations
            // Also note: don't delete the manually created annotations
            final int maxDel = 5000;
            final String sql = "delete from juxta_annotation where set_id=? and manual=? order by id limit "+maxDel;
            int cnt = this.jt.update(sql, set.getId(), 0);
            if (cnt < maxDel ) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {}
        }
    }
    
    @Override
    public void setTokenzedLength(ComparisonSet set, Witness witness, long tokenizedLength) {
        final String sql = "update "+SET_MEMBER_TABLE+" set tokenized_length=? where set_id=? and witness_id=?";
        this.jt.update(sql, tokenizedLength, set.getId(), witness.getId());
    }
    
    @Override
    public long getTokenzedLength(ComparisonSet set, Witness witness) {
        final String sql = "select tokenized_length from "+SET_MEMBER_TABLE+" where set_id=? and witness_id=?";
        return this.jt.queryForLong(sql, set.getId(), witness.getId());
    }
    
    @Override
    public List<Usage> getUsage(ComparisonSet set) {
        List<Witness> witnesses = getWitnesses(set);
        List<Usage> usage = new ArrayList<Usage>();
        String nmSql = "select name from juxta_source where id=?";
        Set<Long> srcIds = new HashSet<Long>();
        for ( Witness w : witnesses) {
            usage.add( new Usage(Usage.Type.WITNESS, w.getId(), w.getName()) );
            
            // don't add multiple copies of the same source!
            if ( srcIds.contains(w.getSourceId()) == false ) {
                srcIds.add( w.getSourceId());
                String srcName = this.jt.queryForObject(nmSql, String.class, w.getSourceId());
                usage.add( new Usage(Usage.Type.SOURCE, w.getSourceId(), srcName) );
            }
        }
        return usage;
    }
    
    @Override
    public boolean exists(final Workspace ws, final String setName) {
        final String sql = 
            "select count(*) as cnt from " + this.tableName +" where name = ? and workspace_id=?";
        int cnt = this.jt.queryForInt(sql,  setName, ws.getId() );
        return (cnt > 0);
    }
    
    @Override
    public boolean hasAlignment(ComparisonSet set, Long alignmentId) {
        final String sql = 
            "select count(*) as cnt from juxta_alignment where set_id=? and id=?";
        int cnt = this.jt.queryForInt(sql,  set.getId(), alignmentId);
        return (cnt > 0);
    }

    @Override
    public boolean isWitness(ComparisonSet set, Witness witness) {
        final String sql = "select count(*) as cnt from " + SET_MEMBER_TABLE + 
            " where set_id = ? and witness_id=?";
        int cnt = this.jt.queryForInt(sql,  set.getId(), witness.getId());
        return (cnt > 0);
    }

    @Override
    public void addWitnesses( final ComparisonSet set, final Set<Witness> witnesses) {
        if ( witnesses.size() > 0 ) {
            clearCollationData( set );
            
            final long setId = set.getId();
            final List<SqlParameterSource> batch = new ArrayList<SqlParameterSource>(
                witnesses.size());
            for (Witness witness : witnesses ) {
                batch.add(new MapSqlParameterSource()
                    .addValue("set_id", setId)
                    .addValue("witness_id", witness.getId()));
            }
            this.memberInsert.executeBatch(batch.toArray(new SqlParameterSource[batch.size()]));
            updateLastUpdatedTime( set );
        }
    }
    
    private void updateLastUpdatedTime(final ComparisonSet set) {
        final String sql = "update "+this.tableName+" set updated = ? where id = ?";
        this.jt.update(sql, new Date(), set.getId());
    }

    @Override
    public List<Witness> getWitnesses( final ComparisonSet set ) {
        return this.witnessDao.find( set );
    }

    @Override
    public void deleteAllWitnesses(final ComparisonSet set) {
        this.jt.update("delete from "+SET_MEMBER_TABLE+" where set_id=?", set.getId());
        set.setStatus(Status.NOT_COLLATED);
        update( set );
        
        this.taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                clearCollationData(set); 
            }
        });
    }
    
    @Override
    public void deleteWitness(final ComparisonSet set, final Witness witness) {
        // delete witness and update time. These happen quickly.
        // Clearing of collation data can be LONG. put it in a worker
        final String sql = "delete from "+SET_MEMBER_TABLE+" where set_id=? and witness_id=?";
        this.jt.update(sql, set.getId(), witness.getId() );
        updateLastUpdatedTime( set );
        set.setStatus(Status.NOT_COLLATED);
        update( set );
        
        this.taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                clearCollationData(set); 
            }
        });
    }
    
    @Override
    public void update(final ComparisonSet set) {
        this.jt.update("update " + this.tableName + 
            " set name = ?, status=?, updated=? where id = ?", 
            set.getName(), set.getStatus().toString(), new Date(), set.getId());
    }
    
    
    @Override
    public ComparisonSet find( final Workspace ws, final String setName) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select id, name, status, workspace_id, created, updated ");
        sql.append(" from "+this.tableName+" where name = ? and workspace_id=?");
        ComparisonSet set = DataAccessUtils.uniqueResult(jt.query(sql.toString(), SET_ROW_MAPPER, 
            setName, ws.getId()));
        return set;
    }
    
    @Override
    public ComparisonSet find(Long id) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select id, name, status, workspace_id, created, updated ");
        sql.append(" from "+this.tableName+" where id = ?");
        ComparisonSet set = DataAccessUtils.uniqueResult(jt.query(sql.toString(), SET_ROW_MAPPER, id));
        return set;
    }

    @Override
    protected SqlParameterSource toInsertData(ComparisonSet object) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", object.getName());
        ps.addValue("status", object.getStatus().toString());
        ps.addValue("workspace_id", object.getWorkspaceId());
        ps.addValue("created", new Date());
        return ps;
    }

    @Override
    public void delete(final ComparisonSet set) {
        if ( set.getStatus().equals(Status.DELETED) == false ) {
            set.setName(set.getName()+"-DELETED-"+System.currentTimeMillis());
            set.setStatus(Status.DELETED);
            update(set);
            this.taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    clearCollationData(set);
                    jt.update("delete from " + tableName + " where id = ?", set.getId());
                }
            });
        }
    }

    @Override
    public List<ComparisonSet> list( final Workspace ws) {
        final String sql = "select id, name, status, workspace_id, created, updated from "
            +this.tableName+" where workspace_id=? and status!=? order by created desc";
        return this.jt.query(sql, SET_ROW_MAPPER, ws.getId(), ComparisonSet.Status.DELETED.toString());
    }
    
    @Override
    public CollatorConfig getCollatorConfig( final ComparisonSet set ) {
        final String sql = "select * from "+CFG_TABLE+" where set_id=?";
        return DataAccessUtils.uniqueResult( this.jt.query(sql, CFG_ROW_MAPPER, set.getId()));
    }
    
    @Override
    public void updateCollatorConfig( final ComparisonSet set, final CollatorConfig cfg ) {
        final String sql = "delete from "+CFG_TABLE+" where set_id=?";
        this.jt.update(sql, set.getId());
        createCollatorConfig(set.getId(), cfg);
        updateLastUpdatedTime( set );
    }
    
    private void createCollatorConfig( final Long setId, final CollatorConfig cfg ) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("filter_case", cfg.isFilterCase() );
        ps.addValue("filter_punctuation", cfg.isFilterPunctuation() );
        ps.addValue("filter_whitespace", cfg.isFilterWhitespace() );
        ps.addValue("hyphenation_filter", cfg.getHyphenationFilter().toString() );
        ps.addValue("set_id", setId);
        this.configInsert.execute( ps );
    }
    
    /**
     * Mapper class for comparison set
     * @author loufoster
     *
     */
    private static class SetRowMapper implements RowMapper<ComparisonSet> {
        @Override
        public ComparisonSet mapRow(ResultSet rs, int rowNum) throws SQLException {
            final ComparisonSet cs = new ComparisonSet();
            cs.setId(rs.getLong("id"));
            cs.setName(rs.getString("name"));
            cs.setStatus( rs.getString("status") );
            cs.setWorkspaceId( rs.getLong("workspace_id"));
            cs.setCreated( rs.getTimestamp("created"));
            cs.setUpdated( rs.getTimestamp("updated"));
            return cs;
        }   
    }
    
    /**
     * Mapper class for tokenizer config
     * @author loufoster
     *
     */
    private static class CollatorConfigRowMapper implements RowMapper<CollatorConfig> {
        @Override
        public CollatorConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
            final CollatorConfig cfg = new CollatorConfig();
            cfg.setId( rs.getLong("id") );
            cfg.setFilterCase( rs.getBoolean("filter_case") );
            cfg.setFilterPunctuation( rs.getBoolean("filter_punctuation") );
            cfg.setFilterWhitespace( rs.getBoolean("filter_whitespace") );
            cfg.setHyphenationFilter( HyphenationFilter.valueOf(rs.getString("hyphenation_filter")) );
            return cfg;
        }
    }
}
