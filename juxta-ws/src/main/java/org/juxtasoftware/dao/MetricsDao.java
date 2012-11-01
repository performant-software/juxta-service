package org.juxtasoftware.dao;

import java.util.List;

import org.juxtasoftware.model.Metrics;

public interface MetricsDao extends JuxtaDao<Metrics> {
    List<Metrics> list();
}
