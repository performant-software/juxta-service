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
import org.juxtasoftware.model.ResourceInfo;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Usage;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.util.LuceneHelper;
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

    @Autowired private TextRepository textRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LuceneHelper lucene;
    
    private SimpleJdbcInsert insert;
    private static final String TABLE_NAME = "juxta_source";
    private static final XMLInputFactory2 XML_INPUT_FACTORY = XML.createXMLInputFactory();
    
    @Override
    public void afterPropertiesSet() throws Exception {
        this.insert = new SimpleJdbcInsert(jdbcTemplate).withTableName(TABLE_NAME).usingGeneratedKeyColumns("id");
    }
    
    @Override
    public Long create(final Workspace ws, final String name, final Source.Type type, Reader contentReader)
        throws IOException, XMLStreamException {

        Text txtContent = null;
        if ( type.equals(Source.Type.XML)) {
            txtContent = this.textRepository.create(XML_INPUT_FACTORY.createXMLStreamReader(contentReader));
        } else {
            txtContent = this.textRepository.create(contentReader);
        }
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", name);
        ps.addValue("content_id", ((RelationalText) txtContent).getId());
        ps.addValue("content_type", type.toString());
        ps.addValue("workspace_id", ws.getId());
        ps.addValue("created", new Date());
        Long srcId = (Long)this.insert.executeAndReturnKey( ps );
        
        // add the new source to the lucene index
        Long textId = ((RelationalText)txtContent).getId();
        Reader r = getContentReader( textId );
        this.lucene.addDocument("source", ws.getName(), srcId, name, textId, r);
        
        return srcId;
    }
    
    private String getWorkspaceName(Long id) {
        String sql = "select name from juxta_workspace where id=?";
        return this.jdbcTemplate.queryForObject(sql, String.class, id);
    }
    
    @Override
    public ResourceInfo getInfo( final Long sourceId ) {
        final String sql = 
            "select w.id as id, w.name as name,w.created as created,w.updated as updated,ws.name as workspace " +
            " from juxta_source w inner join juxta_workspace ws on workspace_id = ws.id where w.id=?";
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql, new RowMapper<ResourceInfo>(){

            @Override
            public ResourceInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new ResourceInfo(rs.getLong("id"), rs.getString("workspace"), rs.getString("name"), rs.getTimestamp("created"), rs.getTimestamp("updated"));
            }}, sourceId));
    }
    
    @Override
    public void update(final Source src, final String newName) {
        String sql = "update juxta_source set name=?, updated=? where id=?";
        this.jdbcTemplate.update(sql, newName, new Date(), src.getId());      
    }
    
    @Override
    public void update( Source src, final String newName, final Reader contentReader) throws IOException, XMLStreamException {
        
        // create a new text entry for this source
        Text txtContent = null;
        if ( src.getType().equals(Source.Type.XML) ) {
            txtContent = this.textRepository.create(XML_INPUT_FACTORY.createXMLStreamReader(contentReader));
        } else {
            txtContent = this.textRepository.create(contentReader);
        }
        Long contentId = ((RelationalText) txtContent).getId();
        Long oldContentID = ((RelationalText)src.getText()).getId();
        src.setName(newName);
        src.setText(txtContent);
        
        String sql = "update juxta_source set name=?, content_id=?, updated=? where id=?";
        this.jdbcTemplate.update(sql, newName, contentId, new Date(), src.getId() );
        
        // delete the old content!
        this.jdbcTemplate.update("delete from text_content where id=?", oldContentID);
        
        // Update the index: remove the old and add the updted src as new
        String ws = getWorkspaceName(src.getWorkspaceId());
        this.lucene.deleteDocument(oldContentID);
        this.lucene.addDocument("source", ws, src.getId(), src.getName(), contentId, getContentReader(contentId) );
    }

    @Override
    public Reader getContentReader( final Source src ) {
        return getContentReader( ((RelationalText)src.getText()).getId() );
    }
    private Reader getContentReader( final Long textId ) {
        final String sql = "select content from text_content where id=?";
        return DataAccessUtils.uniqueResult( this.jdbcTemplate.query(sql, new RowMapper<Reader>(){
            @Override
            public Reader mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getCharacterStream("content");
            }}, textId ));
    }
    
    
    @Override
    public String getRootElement(Source src) {
        if ( src.getType().equals(Source.Type.XML) == false ) {
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
        this.lucene.deleteDocument( ((RelationalText)obj.getText()).getId() );
        this.jdbcTemplate.update("delete from "+TABLE_NAME+" where id=?", obj.getId());
        this.textRepository.delete(obj.getText());
    }

    @Override
    public List<Source> list( final Workspace ws) {
        return jdbcTemplate.query(buildFinderSQL("where workspace_id=? order by created desc"), ROW_MAPPER, ws.getId());
    }

    @Override
    public boolean exists(final Workspace ws, final String name) {
        List<Source> sources = jdbcTemplate.query(buildFinderSQL("where s.name = ?"), ROW_MAPPER, name);
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
    
    @Override
    public Source find(final Long workspaceId, final String name) {
        return DataAccessUtils.uniqueResult(
            this.jdbcTemplate.query(buildFinderSQL("where s.workspace_id=? and s.name = ?"), 
                ROW_MAPPER, workspaceId, name));
    }
    
    @Override
    public List<Usage> getUsage(Source src) {
        // First pass, find all witnesses that have been created from this source
        final String witnessSql = "select id,name from juxta_witness where source_id=?";
        List<Usage> usage =  this.jdbcTemplate.query(witnessSql, new RowMapper<Usage>() {
            @Override
            public Usage mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Usage(Usage.Type.WITNESS, rs.getLong("id"), rs.getString("name"));
            }
            
        }, src.getId());
        
        // find all of the sets that use these witnesses. Add these to the initial list
        if ( usage.size() > 0 ) {
            StringBuilder ids = new StringBuilder();
            for (Usage u : usage) {
                if ( ids.length() > 0) {
                    ids.append(",");
                }
                ids.append( u.getId() );
            }
            String setSql = "select distinct set_id,name from juxta_comparison_set_member " +
            		"inner join juxta_comparison_set on juxta_comparison_set.id = set_id " +
            		"where witness_id in ("+ids+")";
            usage.addAll( this.jdbcTemplate.query(setSql, new RowMapper<Usage>(){
                @Override
                public Usage mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new Usage(Usage.Type.COMPARISON_SET, rs.getLong("set_id"), rs.getString("name"));
                }
                
            }));
        }
        return usage;
    }
    
    @Override
    public String makeUniqueName(final Workspace ws, final String origName) {
        final String sql = "select `name` from juxta_source where `name` like ?";
        int maxNum = 0;
        List<String> names = this.jdbcTemplate.queryForList(sql, String.class, origName+"-%");
        for (String name : names ) {
            String numStr = name.substring(origName.length()+1);
            if ( numStr.length() > 0 ) {
                try {
                    int num = Integer.parseInt(numStr);
                    if ( num > maxNum ) {
                        maxNum = num;
                    }
                } catch (Exception e) {}
            }
        }
        return origName+"-"+(maxNum+1);
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
        return SQL.select(tableName, "id", "name", "content_type", "workspace_id", "created");
    }

    public static Source mapSourceFrom(ResultSet rs, String prefix, Text text) throws SQLException {
        final Source source = new Source();
        source.setId(rs.getLong(prefix + "_id"));
        source.setName(rs.getString(prefix + "_name"));
        source.setType(rs.getString(prefix + "_content_type"));
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
