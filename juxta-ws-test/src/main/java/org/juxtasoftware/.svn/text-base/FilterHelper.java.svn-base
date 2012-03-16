package org.juxtasoftware;

import java.util.UUID;

public class FilterHelper {
    private String name = null;
    private final String baseUrl;
    
    public FilterHelper(String baseUrl ) {
        this.baseUrl = baseUrl+"/filter";
    }
    public void delete(Long filterId) throws Exception {
        Helper.delete(this.baseUrl, filterId);
    }

    public Long createTokenFilter() throws Exception {
        this.name = "filter"+UUID.randomUUID().hashCode();
        String json = 
            "{'name':'"+this.name+"','qnames':" +
                    "[{namespace: 'http://juxtasoftware.org/ns', localName: 'token'}]}";
        return Helper.post(this.baseUrl, json);
    }
    
    public Long createHeatmapFilter() throws Exception {
        this.name = "diff"+UUID.randomUUID().hashCode();
        String json = 
            "{'name':'"+this.name+"','qnames':" +
                    "[{namespace: 'http://juxtasoftware.org/ns', localName: 'change'}," +
                    " {namespace: 'http://juxtasoftware.org/ns', localName: 'addDel'}]}";
        return Helper.post(this.baseUrl, json);
    }
    
    /**
     * returns the name of the last created profile
     * @return
     */
    public String getName() {
        return this.name;
    }
}
