package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.juxtasoftware.dao.QNameDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import eu.interedition.text.Name;
import eu.interedition.text.mem.SimpleName;

@Repository
public class QNameDaoImpl implements QNameDao {
    @Autowired protected JdbcTemplate jdbcTemplate;
    
    @Override
    public List<Name> list() {
        StringBuilder sql = new StringBuilder("select local_name, namespace from text_qname");
        return this.jdbcTemplate.query(sql.toString(), new RowMapper<Name>() {

            @Override
            public Name mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new SimpleName(rs.getString("namespace"), rs.getString("local_name"));
            }
            
        });
    }

}
