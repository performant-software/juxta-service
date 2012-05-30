package org.juxtasoftware.dao.impl;

import static eu.interedition.text.rdbms.RelationalTextRepository.selectTextFrom;

import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.RevisionInfo;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
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
public class WitnessDaoImpl extends JuxtaDaoImpl<Witness> implements WitnessDao {

    @Autowired TextRepository textRepository;
    
    public WitnessDaoImpl() {
        super("juxta_witness");
    }
    
    @Override
    public void rename(final Witness witness, final String newName) {
        final String sql = "update "+this.tableName+" set name = ? where id = ?";
        this.jt.update(sql, newName, witness.getId());
    }
    
    @Override
    public void updateContent(final Witness witness, final Text newContent) {
        String sql = "update "+this.tableName+" set text_id=?, updated=? where id=?";
        this.jt.update(sql, 
            ((RelationalText)newContent).getId(),
            new Date(),
            witness.getId() );
    }
    
    @Override
    public Reader getContentStream(Witness witness) {
        final String sql = "select content from text_content where id=?";
        return DataAccessUtils.uniqueResult( this.jt.query(sql, new RowMapper<Reader>(){
            @Override
            public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getCharacterStream("content");
            }}, ((RelationalText)witness.getText()).getId() ) );
    }
    
    @Override
    public List<Witness> list( final Workspace ws) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" where workspace_id=? order by created desc, updated desc");
        return jt.query(sql.toString(), new WitnessMapper(), ws.getId());
    }
    
    @Override
    public void delete(Witness obj) {
        this.jt.update("delete from "+this.tableName+" where id = ?", obj.getId());
        this.textRepository.delete(obj.getText());
    }

    @Override
    protected SqlParameterSource toInsertData(Witness object) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", object.getName());
        ps.addValue("fragment_start", object.getFragment().getStart());
        ps.addValue("fragment_end", object.getFragment().getEnd());
        ps.addValue("source_id", object.getSourceId());
        ps.addValue("xslt_id", object.getXsltId());
        ps.addValue("text_id", ((RelationalText) object.getText()).getId());
        ps.addValue("workspace_id", object.getWorkspaceId());
        ps.addValue("created", new Date());
        return ps;
    }
    
    @Override
    public Set<Witness> find(ComparisonSet set) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" join juxta_comparison_set_member csm on csm.witness_id = w.id");
        sql.append(" where csm.set_id = ?");
        return new HashSet<Witness>( 
            this.jt.query(sql.toString(), new WitnessMapper(), set.getId()));
    }
    
    @Override
    public Witness find(Workspace ws, String title) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" where w.workspace_id = ? and w.name = ?");
        return DataAccessUtils.uniqueResult( this.jt.query(sql.toString(), 
            new WitnessMapper(), ws.getId(), title));
    }
    
    @Override
    public boolean exists( final Workspace ws, final String title) {
        final String sql = "select count(*) as cnt from " +this.tableName+ " where workspace_id=? and name=?";
        int cnt = this.jt.queryForInt(sql, ws.getId(), title);
        return ( cnt > 0);
    }
    
    @Override
    public Witness find(ComparisonSet set, String title) {
        StringBuilder sql = getSqlBuilder();
        sql.append(" join juxta_comparison_set_member csm on csm.witness_id = w.id");
        sql.append(" where csm.set_id = ? and w.name = ?");
        return DataAccessUtils.uniqueResult( this.jt.query(sql.toString(), 
            new WitnessMapper(), set.getId(), title));
    }
    
    private StringBuilder getSqlBuilder() {
        final StringBuilder sql = new StringBuilder();
        sql.append("select w.id as w_id, w.name as w_name, w.created as w_created, w.updated as w_updated, ");
        sql.append("       w.source_id as w_source_id, w.xslt_id as w_xslt_id,");
        sql.append("       w.fragment_start as w_fragment_start, ");
        sql.append("       w.fragment_end as w_fragment_end, ");
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
                this.jt.query(sql.toString(), new WitnessMapper(), id));
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
        this.jt.batchUpdate(sql.toString(), new BatchPreparedStatementSetter() {

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
        int cnt = this.jt.queryForInt(sql, witness.getId());
        return ( cnt > 0);
    }
    
    @Override
    public List<RevisionInfo> getRevisions(final Witness witness) {
        StringBuilder sql = new StringBuilder();
        sql.append("select id,witness_id,revision_type,start,end,content,is_included"); 
        sql.append(" from juxta_revision where witness_id=?");
        return this.jt.query(sql.toString(), new RowMapper<RevisionInfo>(){

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
        List<Usage> usage = this.jt.query(setSql, new RowMapper<Usage>(){
            @Override
            public Usage mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Usage(Usage.Type.COMPARISON_SET, rs.getLong("set_id"), rs.getString("name"));
            }
            
        },witness.getId());
        
        // include the source (that we already know) that was used to generate this witness
        String nmSql = "select name from juxta_source where id=?";
        String srcName = this.jt.queryForObject(nmSql, String.class, witness.getSourceId());
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
            witness.setFragment(new Range(rs.getInt("w_fragment_start"), 
                                          rs.getInt("w_fragment_end")));
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
