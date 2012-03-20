package org.juxtasoftware.dao.impl;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.FragmentFormatter;
import org.juxtasoftware.util.RangedTextReader;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Name;
import eu.interedition.text.Range;
import eu.interedition.text.rdbms.RelationalName;

@Repository
public class AlignmentDaoImpl implements AlignmentDao, InitializingBean  {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private WitnessDao witnessDao;
    private static final String TABLE_NAME = "juxta_alignment";
    
    private static final int FRAG_SIZE = 30;
    private static final int DEL_FRAG_SIZE = 45;
    
    private SimpleJdbcInsert jdbcInsert;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.jdbcInsert = new SimpleJdbcInsert(this.jdbcTemplate).withTableName(TABLE_NAME);
    }
    
    @Override
    public int create( final List<Alignment> alignments) {
        final List<SqlParameterSource> psBatch = new ArrayList<SqlParameterSource>();
        for (Alignment align : alignments) {
            psBatch.add(toSqlParameterSource(align));
        }
        int[] rowsAffected = this.jdbcInsert.executeBatch(psBatch.toArray(new SqlParameterSource[psBatch.size()]));
        return rowsAffected.length;
    }
    
   @Override
   public void clear(ComparisonSet set){
       clear(set, false);
   }
   
   @Override
   public void clear( final ComparisonSet set, boolean force) {
       if ( force == false ) {
           final String sql = "delete from "+TABLE_NAME+" where set_id=? and manual=?";
           this.jdbcTemplate.update(sql, set.getId(), 0);
       } else {
           final String sql = "delete from "+TABLE_NAME+" where set_id=?";
           this.jdbcTemplate.update(sql, set.getId());
       }
    }
    
    protected SqlParameterSource toSqlParameterSource(Alignment align) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("set_id", align.getComparisonSetId() );
        ps.addValue("qname_id", ((RelationalName)align.getName()).getId() );
        ps.addValue("group_num", align.getGroup() );
        ps.addValue("manual", align.isManual() );
        ps.addValue("edit_distance", align.getEditDistance() );
        List<AlignedAnnotation> annos = align.getAnnotations();
        ps.addValue("annotation_a_id", annos.get(0).getId() );
        ps.addValue("annotation_b_id", annos.get(1).getId() );
        return ps;
    }
    
    @Override
    public void delete(final Long id) {
        final String sql = "delete from "+TABLE_NAME+" where id=?";
        this.jdbcTemplate.update(sql, id);
    }
    
    @Override
    public List<Alignment> list(final AlignmentConstraint constraint ) {
        StringBuilder sql = alignmentAnnotationsQuery();
        sql.append(" where a.set_id = ").append(constraint.getSetId());
        if ( constraint.getFilter() != null ) {
            sql.append(" and aqn.id in (");
            int cnt = 0;
            for ( Name qname : constraint.getFilter().getQNames() ) {
                if ( cnt > 0){
                    sql.append(",");
                }
                cnt++;
                sql.append( ((RelationalName)qname).getId() );
            }
            sql.append(")");
        }
       
        // get the list of alignments that involve the base witness
        AlignmentsMapper mapper = new AlignmentsMapper( constraint );
        this.jdbcTemplate.query(sql.toString(), mapper );
        return mapper.getAlignments();    
    }

    /**
     * Gener query to get all annotations for all alignments.
     * Use this as a basis for all others 
     * @return
     */
    private final StringBuilder alignmentAnnotationsQuery( ) {
        StringBuilder sb = new StringBuilder();
        sb.append("select a.id as a_id, a.set_id as a_set_id, a.edit_distance as edit_distance, ");
        sb.append(" a.group_num as group_num, a.manual as manual, ");
        sb.append(" aqn.id as aqn_id, aqn.namespace as namespace, aqn.local_name as local_name, ");
        sb.append(" w1.id as w1_id, a1.id as a1_id, a1.range_start as a1_start, a1.range_end as a1_end, ");
        sb.append(" a1_qn.id as a1_qn_id, a1_qn.namespace as a1_namespace, a1_qn.local_name as a1_local_name, ");
        sb.append(" w2.id as w2_id, a2.id as a2_id, a2.range_start as a2_start, a2.range_end as a2_end, ");
        sb.append(" a2_qn.id as a2_qn_id, a2_qn.namespace as a2_namespace, a2_qn.local_name as a2_local_name ");
        sb.append(" from ").append(TABLE_NAME).append(" as a "); 
        sb.append(" inner join text_qname as aqn on a.qname_id=aqn.id ");
        
        sb.append(" inner join text_annotation as a1 on a1.id=a.annotation_a_id ");
        sb.append(" inner join text_qname as a1_qn on a1.name=a1_qn.id ");
        sb.append(" inner join juxta_witness as w1 on w1.text_id=a1.text ");
        
        sb.append(" inner join text_annotation as a2 on a2.id=a.annotation_b_id ");
        sb.append(" inner join text_qname as a2_qn on a2.name=a2_qn.id ");
        sb.append(" inner join juxta_witness as w2 on w2.text_id=a2.text ");
        return sb;
    }

    @Override
    public Alignment find(final ComparisonSet set, final Long id) {
        StringBuilder sql = alignmentAnnotationsQuery();
        sql.append(" where a.id = ").append(id);
        AlignmentConstraint constraint = new AlignmentConstraint(set);
        constraint.setAlignmentId(id);
        AlignmentsMapper mapper = new AlignmentsMapper(constraint );
        this.jdbcTemplate.query(sql.toString(), mapper );
        if  (mapper.getAlignments().isEmpty() ) {
            return null;
        } else {
            Alignment alignment = mapper.getAlignments().get(0);
            getFragments( alignment );
            return alignment;
        }
    }
    
    private void getFragments( Alignment align) {

        for (AlignedAnnotation a : align.getAnnotations()) {
        
            // generate a range that has the alignment as its center
            // and extends away for some context
            Range origRange = new Range( a.getRange() ); 
            Range witnessRange = new Range( a.getRange() ); 
            Witness witness = this.witnessDao.find(a.getWitnessId());
            long start = witnessRange.getStart()-FRAG_SIZE;
            if ( witnessRange.length() == 0 ) {
                start = witnessRange.getStart()-DEL_FRAG_SIZE;
            }
            start = Math.max(0, start);
            long witnessLen = witness.getText().getLength();
            long end = Math.min(witnessLen, witnessRange.getEnd()+FRAG_SIZE);
            String frag;
            try {
                // read the full fragment
                final RangedTextReader reader = new RangedTextReader();
                reader.read( this.witnessDao.getContentStream(witness), new Range(start, end) );
                frag = reader.toString();
            } catch (IOException e) {
                // couldn't get fragment. skip it for now
                return;
            }
            
            a.setFragment( 
                FragmentFormatter.format(frag, origRange, new Range(start,end), witness.getText().getLength() ));
        }

    }
    
    /**
     * Mapper for a series of alignments
     * @author loufoster
     *
     */
    private static class AlignmentsMapper implements RowMapper<Void> {
        private List<Alignment> alignments = new ArrayList<Alignment>();
        private final AlignmentConstraint constraint;
        
        public AlignmentsMapper(AlignmentConstraint constraint) {
            this.constraint = constraint;
        }

        public List<Alignment> getAlignments() {
            return this.alignments;
        }
        
        @Override
        public Void mapRow(ResultSet rs, int rowNum) throws SQLException {
            Long alignId = rs.getLong("a_id");
            if (this.constraint.getAlignmentId() != null && 
                this.constraint.getAlignmentId().equals(alignId) == false ) {
                return null;
            }
            
            // grab some constraint data for readability
            Range tgtRange = this.constraint.getRange();
            Long baseId = this.constraint.getBaseId();
            
            // create a new alignment with all data except annotations
            Alignment align = new Alignment();
            align.setId(alignId );
            RelationalName qname = new RelationalName(rs.getString("namespace"), rs.getString("local_name"), rs.getLong("aqn_id"));
            align.setName( qname );
            if ( rs.getBoolean("manual")) {
                align.setManual();
            }
            align.setComparisonSetId( this.constraint.getSetId() );
            align.setEditDistance( rs.getInt("edit_distance") );
            align.setGroup( rs.getInt("group_num") );
            
            // collect annotation info. Toss the entire thing if constraints apply
            for (int i=1;i<=2;i++) {
                Long annoId = rs.getLong("a"+i+"_id");
                Long witnessId = rs.getLong("w"+i+"_id");
                Range range = new Range(rs.getInt("a"+i+"_start"), rs.getInt("a"+i+"_end"));
                if ( tgtRange != null ) {
                    if ( this.constraint.isBaseless() || witnessId.equals( baseId ) ) {
                        if ( range.getStart() < tgtRange.getStart() ||
                             range.getEnd() > tgtRange.getEnd() ) {
                            return null;
                        }
                    }
                }
                RelationalName qn = new RelationalName(
                    rs.getString("a"+i+"_namespace"), rs.getString("a"+i+"_local_name"), rs.getLong("a"+i+"_qn_id"));
                align.addAnnotation( new AlignedAnnotation(qn, witnessId, annoId, range));
            }
            
            // check to see if the witnesses that make up this alignment
            // are in the requested list of witness ids. If not, we don't care so toss it.
            if ( matchesFilter(constraint.getWitnessIdFilter(), align.getAnnotations()) == false) {
                return null;
            }

            // If we got here, itsa keeper
            this.alignments.add(align);
            
            return null;
        } 
        
        private boolean matchesFilter(Set<Long> witnessIds, List<AlignedAnnotation> annotations) {
            int matchCnt = 0;
            for (Long witnessId : witnessIds ) {
                for (AlignedAnnotation annotation : annotations ) {
                    if ( annotation.getWitnessId().equals( witnessId)  ) {
                        matchCnt++;
                    }
                }
            }
            return (matchCnt == witnessIds.size());
        }
    }
}
