package org.juxtasoftware.resource;

import java.io.IOException;

import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.resource.heatmap.HeatmapView;
import org.juxtasoftware.resource.sidebyside.SideBySideView;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class SetViewResource extends BaseResource {

    public enum Mode {UNDEFINED, HEAT_MAP, SIDE_BY_SIDE};
    
    @Autowired private ComparisonSetDao setDao;
    @Autowired private HeatmapView heatmapView;
    @Autowired private SideBySideView sideBySideView;

    private Mode mode;
    private ComparisonSet set;
    
    @Override
    protected void doInit() throws ResourceException {

        super.doInit();
        
        Long id = getIdFromAttributes("id");
        if ( id == null ) {
            return;
        }
        this.set = this.setDao.find(id);
        if (validateModel(this.set) == false) {
            return;
        }
        
        if (getQuery().getValuesMap().containsKey("mode") ) {
            String view = getQuery().getValuesMap().get("mode").toLowerCase();
            if ( view.equals("sidebyside") ) {
                this.mode = Mode.SIDE_BY_SIDE;
            } else if ( view.equals("heatmap")){
                this.mode = Mode.HEAT_MAP;
            } else {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Unsupported view requested");
            }
        } else {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "Missing required view parameter");
        }
    }
    
    @Get("html")
    public Representation toHtml() throws IOException {
        LOG.info("Get "+this.mode+" view of comparison set "+this.set.getId());
        if ( this.set.isCollated() == false ) {
            setStatus(Status.CLIENT_ERROR_NOT_FOUND);
            return toTextRepresentation("This set is not collated");
        }
        switch ( this.mode ) {
            case HEAT_MAP:
                return this.heatmapView.toHtml(this, this.set);
            case SIDE_BY_SIDE:
                return this.sideBySideView.toHtml(this, this.set);
            default:
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
                return null;
        }
    }
    
    @Delete
    public void handleDelete() {
        if ( this.mode.equals(Mode.HEAT_MAP) ) {
            LOG.info("Delete cached heatmap data for "+set.toString());
            this.heatmapView.delete( this.set );
        }
    }
}
