package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Metrics;
import org.juxtasoftware.model.Workspace;

public interface MetricsDao extends JuxtaDao<Metrics> {
    List<Metrics> list();
    Metrics get( final Workspace ws );
    void update( final Metrics m );
}
