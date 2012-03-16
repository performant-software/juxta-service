package org.juxtasoftware.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.juxtasoftware.dao.TemplateDao;
import org.juxtasoftware.model.Template;
import org.juxtasoftware.model.Template.WildcardQName;
import org.juxtasoftware.model.Workspace;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class TemplateDaoImpl extends JuxtaDaoImpl<Template> implements TemplateDao {

    public static final String ACTIONS_TABLE_NAME = "juxta_template_tag_action";
    private SimpleJdbcInsert tagActionInsert;
    private NamedParameterJdbcTemplate namedParmTemplate;

    public TemplateDaoImpl () {
        super("juxta_template");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.tagActionInsert = new SimpleJdbcInsert(jt).withTableName(ACTIONS_TABLE_NAME);
        this.namedParmTemplate =  new NamedParameterJdbcTemplate( this.jt );
    }

    @Override
    public long create(Template newTemplate) {
        if (newTemplate.isDefault() ) {
            clearOldDefault(newTemplate.getWorkspaceId(),  newTemplate.getRootElement());
        }
        newTemplate.setId(super.create(newTemplate));
        insertTagActions( newTemplate );
        return newTemplate.getId();
    }


    private void clearOldDefault(final Long workspaceId, final WildcardQName rootElement) {
        // Ensure that only one template is set as default in this 
        // workspace by clearing ALL default setting before add or update 
        // a template that has been tagged as default.
        StringBuilder sql = new StringBuilder();
        sql.append("update ").append(this.tableName);
        sql.append(" set is_default = 0 where workspace_id=:ws");
        sql.append(" and root_namespace_uri=:uri ");
        sql.append(" and root_namespace_prefix=:prefix");
        sql.append(" and root_local_name=:local");
        Map<String,Object> params = new HashMap<String, Object>();
        params.put("ws", workspaceId);
        params.put("uri", rootElement.getNamespaceUri());
        params.put("prefix", rootElement.getNamespacePrefix());
        params.put("local", rootElement.getLocalName());
        this.namedParmTemplate.update(sql.toString(), params);
    }

    @Override
    public void delete(Template obj) {
        jt.update("delete from " + tableName + " where id = ?", obj.getId());
    }
    
    @Override
    public List<Template> list( final Workspace ws ) {
        StringBuilder sql = new StringBuilder();
        sql.append( selectTemplateSql() );
        sql.append( "where workspace_id=?");
        return jt.query(sql.toString(), new TemplateMapper(), ws.getId());
    }
    
    @Override
    public boolean exists( final Workspace ws, final String templateName) {
        final String sql = 
            "select count(*) as cnt from juxta_template where name = ? and workspace_id = ?";
        int cnt = this.jt.queryForInt(sql,  templateName, ws.getId() );
        return (cnt > 0);
    }

    @Override
    public Template find( final Workspace ws, final String templateName) {
        StringBuilder sql = new StringBuilder();
        sql.append( selectTemplateSql() );
        sql.append( "where name = ? and workspace_id=?");
        List<Template> res = this.jt.query( sql.toString(), new TemplateMapper(), templateName, ws.getId());
        final Template template = DataAccessUtils.uniqueResult( res );
        if ( template != null ) {
            template.setTagActions(new HashSet<Template.TagAction>(findTagActions(template)));
        }
        return template;
    }
    
    @Override
    public Template find(Long id) {
        StringBuilder sql = new StringBuilder();
        sql.append( selectTemplateSql() );
        sql.append( "where id = ?");
        final Template template = DataAccessUtils.uniqueResult(jt.query(sql.toString(), new TemplateMapper(), id));
        if ( template != null ) {
            template.setTagActions(new HashSet<Template.TagAction>(findTagActions(template)));
        }
        return template;
    }
    
    @Override
    public Template findDefault( final Workspace ws, final String rootEle ) {
        WildcardQName wqn = WildcardQName.fromString(rootEle);
        Map<String,Object> params = new HashMap<String, Object>();
        
        final StringBuilder sql = new StringBuilder();
        sql.append( selectTemplateSql() );
        sql.append(" where workspace_id = :ws ");
        sql.append(" and is_default = :default ");
        params.put("ws", ws.getId());
        params.put("default", true);
        
        if ( wqn.getNamespaceUri().equals("*") == false) {
            sql.append(" and root_namespace_uri = :uri");
            params.put("uri", wqn.getNamespaceUri());
        }
        
        if ( wqn.getNamespacePrefix().equals("*") == false) {
            sql.append(" and root_namespace_prefix = :prefix");
            params.put("uri", wqn.getNamespacePrefix());
        }
        
        sql.append(" and root_local_name = :local");
        params.put("local", wqn.getLocalName());
        
        final Template template = DataAccessUtils.uniqueResult(
            this.namedParmTemplate.query(sql.toString(), params, new TemplateMapper()));
        if ( template != null ) {
            template.setTagActions(new HashSet<Template.TagAction>(findTagActions(template)));
        }
        return template;
    }

    @Override
    public void update(Template template) {
        if ( template.isDefault() ) {
            clearOldDefault(template.getWorkspaceId(), template.getRootElement());
        }
        
        final String sql = 
            "update " + tableName + " set name = :name, " +
                " root_namespace_uri = :uri," +
                " root_namespace_prefix = :prefix, " +
                " root_local_name = :local, " + 
                " is_default = :default " +
                " where id = :id";
        SqlParameterSource params = new MapSqlParameterSource()
            .addValue("name", template.getName())
            .addValue("uri", template.getRootElement().getNamespaceUri() )
            .addValue("prefix", template.getRootElement().getNamespacePrefix() )
            .addValue("local", template.getRootElement().getLocalName() )
            .addValue("default", template.isDefault())
            .addValue("id", template.getId());
        this.namedParmTemplate.update(sql, params);
        jt.update("delete from " + ACTIONS_TABLE_NAME + " where template_id = ?", template.getId());
        insertTagActions(template);
    }

    @Override
    protected SqlParameterSource toInsertData(Template object) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("name", object.getName());
        ps.addValue("root_namespace_uri", object.getRootElement().getNamespaceUri());
        ps.addValue("root_namespace_prefix", object.getRootElement().getNamespacePrefix());
        ps.addValue("root_local_name", object.getRootElement().getLocalName());
        ps.addValue("is_default", object.isDefault());
        ps.addValue("workspace_id", object.getWorkspaceId());
        return ps;
    }

    protected SqlParameterSource toSqlParameterSource(Template template, Template.TagAction a) {
        final MapSqlParameterSource ps = new MapSqlParameterSource();
        ps.addValue("template_id", template.getId());
        ps.addValue("namespace_uri", a.getTag().getNamespaceUri());
        ps.addValue("namespace_prefix", a.getTag().getNamespacePrefix());
        ps.addValue("local_name", a.getTag().getLocalName());
        ps.addValue("action", a.getAction());
        return ps;
    }

    private List<Template.TagAction> findTagActions(final Template template) {
        final StringBuilder sql = new StringBuilder();
        sql.append( selectTagActionSql() );
        sql.append(" where template_id = ?");
        List<Template.TagAction> out = jt.query(sql.toString(), new RowMapper<Template.TagAction>() {
            @Override
            public Template.TagAction mapRow(ResultSet rs, int rowNum) throws SQLException {
                final Template.TagAction a = new Template.TagAction();
                a.setTemplate(template);
                a.setId( rs.getLong( "id"));
                WildcardQName tag = new WildcardQName(
                    rs.getString("namespace_uri"), 
                    rs.getString("namespace_prefix"), 
                    rs.getString("local_name") );
                a.setTag( tag );
                a.setAction(rs.getString("action"));
                return a;
            }
        }, template.getId());
        return out;

    }

    private void insertTagActions(Template template) {
        final Set<Template.TagAction> tagActions = template.getTagActions();
        if (tagActions.isEmpty()) {
            return;
        }

        final List<SqlParameterSource> psBatch = new ArrayList<SqlParameterSource>();
        for (Template.TagAction a : tagActions) {
            psBatch.add(toSqlParameterSource(template, a));
        }
        tagActionInsert.executeBatch(psBatch.toArray(new SqlParameterSource[psBatch.size()]));
    }

    private String selectTemplateSql() {
        StringBuilder sql = new StringBuilder();
        sql.append("select id, name, root_namespace_uri, root_namespace_prefix,");
        sql.append(" root_local_name, is_default, workspace_id");
        sql.append(" from ").append(this.tableName).append(" ");
        return sql.toString();
    }

    private static String selectTagActionSql() {
        final StringBuilder sql = new StringBuilder();
        sql.append("select id, namespace_uri, namespace_prefix, local_name, action");
        sql.append(" from ").append( ACTIONS_TABLE_NAME ).append(" ");
        return sql.toString();
    }

    private static class TemplateMapper implements RowMapper<Template> {
        @Override
        public Template mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Template template = new Template();
            template.setId(rs.getLong("id"));
            template.setName(rs.getString("name"));
            template.setDefault( rs.getBoolean("is_default"));
            template.setWorkspaceId( rs.getLong("workspace_id"));
            template.setRootElement( 
                new WildcardQName(
                    rs.getString("root_namespace_uri"), 
                    rs.getString("root_namespace_prefix"), 
                    rs.getString("root_local_name")));  
            return template;
        }
    };
}
