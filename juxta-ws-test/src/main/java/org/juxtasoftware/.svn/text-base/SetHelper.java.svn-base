package org.juxtasoftware;

import java.util.UUID;

import org.apache.log4j.Logger;

public class SetHelper {
    private static final Logger LOG = Logger.getRootLogger();
    private final String baseUrl;
    private Long[] sourceId = new Long[3];
    private Long[] witnessId = new Long[3];
    private final String[] data;
    public static final String[] basic = {
        "The apple is red",
        "The apple is not red",
        "The pear is greeen"};
    
    
    public static final String[] damozel_same = {
        "Surely she leaned o'er me,—her hair",
        "Surely she leaned o'er me, — her hair"
    };
    
    public static final String[] damozel_diff = {
        "The Blessed Damsel. The blessed damsel leaned against",
        "THE BLESSED DAMOZEL The blessed damozel leaned out"
    };
    
    public SetHelper(final String url, final String[] text ) {
        this.baseUrl = url;
        this.data = text;
    }
    
    public Long getWitnessId( int index ) {
        return this.witnessId[index];
    }
    
    public String getWitnessString() {
        StringBuffer sb  = new StringBuffer();
        for ( int i=0; i< this.data.length; i++ ) {
            if ( i>0) {
                sb.append(",");
            }
            sb.append(this.witnessId[i]);
        }
        return sb.toString();
    }
    
    public void setupSourceAndWitness() throws Exception {
        LOG.info("* Create new sources/witnesses");
        for ( int i=0; i < this.data.length; i++) {
            String name = "set-source-"+UUID.randomUUID().hashCode();
            String json = "[ {\"type\": \"txt\", \"name\": \""+name+"\", \"data\": \""+this.data[i]+"\"} ]";
            this.sourceId[i] = Helper.post(this.baseUrl+"/source", json);   
            String xformJson = "{source: \""+this.sourceId[i]+"\"}";
            this.witnessId[i] = Helper.post(this.baseUrl+"/transform", xformJson);
        }
    }
    
    public void cleanupSourceAndWitness() throws Exception {
        LOG.info("* Cleanup test");
        for ( int i=0; i < this.data.length; i++) {
            Helper.delete(this.baseUrl+"/witness", this.witnessId[i]);
            Helper.delete(this.baseUrl+"/source", this.sourceId[i]);
        }
    }
}
