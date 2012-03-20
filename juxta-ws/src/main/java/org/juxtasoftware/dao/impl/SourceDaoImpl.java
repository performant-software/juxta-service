package org.juxtasoftware.dao.impl;

import java.io.IOException;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLInputFactory2;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Workspace;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import com.google.common.base.Strings;

import eu.interedition.text.Text;
import eu.interedition.text.TextRepository;
import eu.interedition.text.rdbms.RelationalText;
import eu.interedition.text.rdbms.RelationalTextRepository;
import eu.interedition.text.util.SQL;
import eu.interedition.text.xml.XML;

@Repository
public class SourceDaoImpl implements SourceDao, InitializingBean {

    @Autowired TextRepository textRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    
    private SimpleJdbcInsert insert;
    private static final String TABLE_NAME = "juxta_source";
    private static final XMLInputFactory2 XML_INPUT_FACTORY = XML.createXMLInputFactory();
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
    }
    
    @Override
    public Long create(final Workspace ws, final String name, final Boolean isXml, Reader contentReader)
        throws IOException, XMLStreamException {

        Text txtContent = null;
        if (isXml) {
            txtContent = this.textRepository.create(XML_INPUT_FACTORY.createXMLStreamReader(contentReader));
        } else {
            txtContent = this.textRepository.create(contentReader);
        }
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("file_name", name);
        ps.addValue("content_id", ((RelationalText) txtContent).getId());
        ps.addValue("workspace_id", ws.getId());
        ps.addValue("created", new Date());
        return (Long)this.insert.executeAndReturnKey( ps );
    }

    @Override
    public Reader getContentReader( final Source src ) {
        final String sql = "select content from text_content where id=?";
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql, new RowMapper<Reader>(){
            @Override
            public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getCharacterStream("content");
            }}, ((RelationalText)src.getText()).getId() ) );
    }
    
    @Override
    public String getRootElement(Source src) {
        if ( src.getText().getType().equals(Text.Type.XML) == false ) {
            return null;
        }
        
        final String sql = "select content from text_content where id=?";
        String tag = this.jdbcTemplate.queryForObject(sql, new RowMapper<String>() {
            @Override
            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                try {
                    return findRoolElement(rs.getCharacterStream("content"));
                } catch (IOException e) {
                    return null;
                }
            }}, ((RelationalText)src.getText()).getId());
        return tag;
    }
    
    private String findRoolElement(Reader reader) throws IOException {
        char[] buff = new char[64];
        Pattern patt = Pattern.compile("<[^?!]");
        StringBuffer tag = new StringBuffer();
        while ( true ) {
            int cnt = reader.read(buff);
            if ( cnt == -1) {
                break;
            } else {
                String data = new String(buff);
                if ( tag.length() == 0 ) {
                    Matcher m = patt.matcher( data );
                    if ( m.find() ) {
                        int p1 = m.start();
                        int p2 = data.indexOf('>', p1);
                        if ( p2 > -1 ) {
                            tag.append( data.substring(p1+1, p2) );
                            break;
                        } else {
                            tag.append( data.substring(p1+1) );
                        }
                    }
                } else {
                    int pos = data.indexOf('>');
                    if ( pos > -1 ) {
                        tag.append( data.substring(0, pos) );
                        break;
                    } else {
                        tag.append( data );
                    }
                }
            }
        }
        int pos = tag.indexOf(" ");
        if ( pos > -1 ) {
            return tag.substring(0,pos);
        }
        return tag.toString();
    }
    
    @Override
    public void delete(Source obj) {
        
        this.jdbcTemplate.update("delete from "+TABLE_NAME+" where id=?", obj.getId());
        this.textRepository.delete(obj.getText());
    }

    @Override
    public List<Source> list( final Workspace ws) {
        return jdbcTemplate.query(buildFinderSQL("where workspace_id=? order by created desc"), ROW_MAPPER, ws.getId());
    }

    @Override
    public boolean exists(final Workspace ws, final String name) {
        List<Source> sources = jdbcTemplate.query(buildFinderSQL("where s.file_name = ?"), ROW_MAPPER, name);
        boolean exists = false;
        for ( Source src : sources ) {
            if ( src.isMemberOf(ws)) {
                exists = true;
                break;
            }
        }
        return exists;
    }
    
    @Override
    public Source find(final Long workspaceId, final Long id) {
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(buildFinderSQL("where s.workspace_id=? and s.id = ?"), 
                ROW_MAPPER, workspaceId, id));
    }

    protected String buildFinderSQL(String whereClause) {
        final StringBuilder sql = new StringBuilder("select ");
        sql.append(selectSourceFrom("s")).append(", ");
        sql.append(RelationalTextRepository.selectTextFrom("t"));
        sql.append(" from juxta_source s");
        sql.append(" join text_content t on s.content_id = t.id");
        if (!Strings.isNullOrEmpty(whereClause)) {
            sql.append(" ").append(whereClause);
        }
        return sql.toString();
    }

    public static String selectSourceFrom(String tableName) {
        return SQL.select(tableName, "id", "file_name", "workspace_id", "created");
    }

    public static Source mapSourceFrom(ResultSet rs, String prefix, Text text) throws SQLException {
        final Source source = new Source();
        source.setId(rs.getLong(prefix + "_id"));
        source.setFileName(rs.getString(prefix + "_file_name"));
        source.setWorkspaceId( rs.getLong(prefix+"_workspace_id"));
        source.setCreated( rs.getTimestamp(prefix+"_created"));
        source.setText(text);
        return source;
    }

    private static final RowMapper<Source> ROW_MAPPER = new RowMapper<Source>() {
        @Override
        public Source mapRow(ResultSet rs, int rowNum) throws SQLException {
            return mapSourceFrom(rs, "s", RelationalTextRepository.mapTextFrom(rs, "t"));
        }
    };
}
