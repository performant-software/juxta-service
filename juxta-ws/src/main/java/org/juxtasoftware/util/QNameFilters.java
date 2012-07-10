package org.juxtasoftware.util;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.QNameFilterDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Workspace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import eu.interedition.text.NameRepository;

/**
 * Helper class to create/get common qname filters.
 * 
 * @author loufoster
 *
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public final class QNameFilters {
    private static final String DIFF_FILTER = "differences";
    private static final String REVISIONS_FILTER = "revisions";
    private static final String TRANSPOSITIONS_FILTER = "transpositions";
    private static final String TOKENS_FILTER = "tokens";
    
    @Autowired private QNameFilterDao filterDao;
    @Autowired private NameRepository qnameRepo;
    @Autowired private WorkspaceDao workspaceDao;
    
    /**
     * Create all common filters
     */
    public void initialize() {
        Workspace pub = this.workspaceDao.getPublic();
        if ( pub == null ) {
            pub = new Workspace();
            pub.setName("public");
            pub.setDescription("Default public workspace");
            this.workspaceDao.create(pub);
        }
        getDifferencesFilter();
        getRevisionsFilter();
        getTokensFilter();
        getTranspositionsFilter();
    }
    
    /**
     * Get a filter for transpositions
     * @return
     */
    public QNameFilter getTranspositionsFilter() {
        QNameFilter filter = this.filterDao.find(TRANSPOSITIONS_FILTER);
        if ( filter == null ) {
            
            filter = new QNameFilter();
            addPublicWorkspace( filter );
            filter.setName(TRANSPOSITIONS_FILTER);
            filter.getQNames().add( this.qnameRepo.get( Constants.TRANSPOSITION_NAME ) );
            this.filterDao.create(filter);
        }
        return filter;
    }
    
    private void addPublicWorkspace( QNameFilter filter ) {
        Workspace ws = this.workspaceDao.getPublic();
        filter.setWorkspaceId(ws.getId());
    }
    /**
     * Get a filter for heatmap-related QNames; Change and ADD/DEL
     * @return
     */
    public QNameFilter getDifferencesFilter() {
        QNameFilter filter = this.filterDao.find(DIFF_FILTER);
        if ( filter == null ) {
            filter = new QNameFilter();
            addPublicWorkspace( filter );
            filter.setName(DIFF_FILTER);
            filter.getQNames().add( this.qnameRepo.get( Constants.CHANGE_NAME ) );
            filter.getQNames().add( this.qnameRepo.get( Constants.ADD_DEL_NAME ) );
            this.filterDao.create(filter);
        }
        return filter;
    }
    
    /**
     * Get a filter for revision-related QNames; add/addSpan/del/delSpan
     * @return
     */
    public QNameFilter getRevisionsFilter() {
        QNameFilter filter = this.filterDao.find(REVISIONS_FILTER);
        if ( filter == null ) {
            filter = new QNameFilter();
            addPublicWorkspace( filter );
            filter.setName(REVISIONS_FILTER);
            filter.getQNames().add( this.qnameRepo.get( Constants.TEI_ADD ) );
            filter.getQNames().add( this.qnameRepo.get( Constants.TEI_ADD_SPAN ) );
            filter.getQNames().add( this.qnameRepo.get( Constants.TEI_DEL ) );
            filter.getQNames().add( this.qnameRepo.get( Constants.TEI_DEL_SPAN ) );
            this.filterDao.create(filter);
        }
        return filter;
    }
    
    /**
     * Get a filter for tokens
     * @return
     */
    public QNameFilter getTokensFilter() {
        QNameFilter filter = this.filterDao.find(TOKENS_FILTER);
        if ( filter == null ) {
            filter = new QNameFilter();
            addPublicWorkspace( filter );
            filter.setName(TOKENS_FILTER);
            filter.getQNames().add( this.qnameRepo.get( Constants.TOKEN_NAME ) );
            this.filterDao.create(filter);
        }
        return filter;
    }
}
