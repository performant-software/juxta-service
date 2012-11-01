package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.MetricsDao;
import org.juxtasoftware.model.Metrics;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

public class MetricsDaoImpl extends JuxtaDaoImpl<Metrics> implements MetricsDao {

    protected MetricsDaoImpl() {
        super("juxta_metrics");
    }

    public void delete(Metrics obj) {
        final String sql = "delete from " + this.tableName + " where id=?";
        this.jt.update(sql, obj.getId());
    }

    @Override
    public Metrics find(Long id) {
        return DataAccessUtils.uniqueResult(
            this.jt.query( getSql()+" where id=?", new MetricsMapper(), id));
    }

    @Override
    public List<Metrics> list() {
        return this.jt.query( getSql(), new MetricsMapper() );
    }
    
    private String getSql() {
        return "select id,name,num_sources, max_src_size_k, min_src_size_k," +
        		"mean_src_size_k, total_src_size_k, secs_collating, started_collations, finished_collations";
    }

    @Override
    protected SqlParameterSource toInsertData(Metrics obj) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("workspace", obj.getWorkspace());
        ps.addValue("num_sources", obj.getNumSources());
        ps.addValue("max_src_size_k", obj.getMaxSourceSize());
        ps.addValue("min_src_size_k", obj.getMinSourceSize());
        ps.addValue("mean_src_size_k", obj.getMeanSourceSize());
        ps.addValue("total_src_size_k", obj.getTotalSourcesSize());
        ps.addValue("secs_collating", obj.getTotalSecsCollating());
        ps.addValue("started_collations", obj.getNumCollationsStarted());
        ps.addValue("finished_collations", obj.getNumCollationsFinished());
        return ps;
    }
    
    
    private static class MetricsMapper implements RowMapper<Metrics> {
        @Override
        public Metrics mapRow(ResultSet rs, int rowNum) throws SQLException {
            Metrics m = new Metrics();
            m.setId( rs.getLong("id"));
            m.setWorkspace( rs.getString("name"));
            m.setNumSources( rs.getInt("num_sources"));
            m.setMaxSourceSize(rs.getInt("max_src_size_k"));
            m.setMinSourceSize(rs.getInt("min_src_size_k"));
            m.setMeanSourceSize(rs.getInt("mean_src_size_k"));
            m.setTotalSourcesSize(rs.getInt("total_src_size_k"));
            m.setTotalSecsCollating(rs.getInt("secs_collating"));
            m.setNumCollationsStarted(rs.getInt("started_collations"));
            m.setNumCollationsFinished(rs.getInt("finished_collations"));
            return m;
        }
    }

}
