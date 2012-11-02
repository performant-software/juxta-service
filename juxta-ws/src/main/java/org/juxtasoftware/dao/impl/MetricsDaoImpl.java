package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.model.Metrics;
import org.juxtasoftware.model.Workspace;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

@Repository
public class MetricsDaoImpl extends JuxtaDaoImpl<Metrics> implements MetricsDao {

    protected MetricsDaoImpl() {
        super("juxta_metrics");
    }

    public void delete(Metrics obj) {
        final String sql = "delete from " + this.tableName + " where id=?";
        this.jt.update(sql, obj.getId());
    }
    
    @Override
    public Metrics get( final Workspace ws ) {
        return DataAccessUtils.uniqueResult(
            this.jt.query( getSql()+" where workspace=?", new MetricsMapper(), ws.getName()));
    }

    @Override
    public Metrics find(Long id) {
        return DataAccessUtils.uniqueResult(
            this.jt.query( getSql()+" where id=?", new MetricsMapper(), id));
    }
    
    @Override
    public void update( final Metrics m ) {
        StringBuilder sb = new StringBuilder("update ");
        sb.append(this.tableName).append(" set ");
        sb.append("num_sources=").append(m.getNumSources()).append(", ");
        sb.append("max_src_size=").append(m.getMaxSourceSize()).append(", ");
        sb.append("min_src_size=").append(m.getMinSourceSize()).append(", ");
        sb.append("mean_src_size=").append(m.getMeanSourceSize()).append(", ");
        sb.append("total_src_size=").append(m.getTotalSourcesSize()).append(", ");
        sb.append("secs_collating=").append(m.getTotalTimeCollating()).append(", ");
        sb.append("started_collations=").append(m.getNumCollationsStarted()).append(", ");
        sb.append("finished_collations=").append(m.getNumCollationsFinished());   
        sb.append(" where id=").append(m.getId());
        this.jt.update(sb.toString());
    }

    @Override
    public List<Metrics> list() {
        return this.jt.query( getSql(), new MetricsMapper() );
    }
    
    private String getSql() {
        return "select *" +
        		" from juxta_metrics";
    }

    @Override
    protected SqlParameterSource toInsertData(Metrics obj) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("workspace", obj.getWorkspace());
        ps.addValue("num_sources", obj.getNumSources());
        ps.addValue("max_src_size", obj.getMaxSourceSize());
        ps.addValue("min_src_size", obj.getMinSourceSize());
        ps.addValue("mean_src_size", obj.getMeanSourceSize());
        ps.addValue("total_src_size", obj.getTotalSourcesSize());
        ps.addValue("secs_collating", obj.getTotalTimeCollating());
        ps.addValue("started_collations", obj.getNumCollationsStarted());
        ps.addValue("finished_collations", obj.getNumCollationsFinished());
        return ps;
    }
    
    
    private static class MetricsMapper implements RowMapper<Metrics> {
        @Override
        public Metrics mapRow(ResultSet rs, int rowNum) throws SQLException {
            Metrics m = new Metrics();
            m.setId( rs.getLong("id"));
            m.setWorkspace( rs.getString("workspace"));
            m.setNumSources( rs.getInt("num_sources"));
            m.setMaxSourceSize(rs.getInt("max_src_size"));
            m.setMinSourceSize(rs.getInt("min_src_size"));
            m.setMeanSourceSize(rs.getInt("mean_src_size"));
            m.setTotalSourcesSize(rs.getInt("total_src_size"));
            m.setTotalTimeCollating(rs.getLong("secs_collating"));
            m.setNumCollationsStarted(rs.getInt("started_collations"));
            m.setNumCollationsFinished(rs.getInt("finished_collations"));
            return m;
        }
    }

}
