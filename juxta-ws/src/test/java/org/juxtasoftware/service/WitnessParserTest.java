package org.juxtasoftware.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.juxtasoftware.service.importer.ps.WitnessParser;
import org.juxtasoftware.service.importer.ps.WitnessParser.PsWitnessInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.xml.sax.SAXException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({"classpath:/applicationContext-dataSource.xml", "classpath:/applicationContext-service.xml"})
public class WitnessParserTest {
    @Autowired private WitnessParser witnessParser;
    
    @Test
    public void parseWitnessList() throws ParserConfigurationException, SAXException, IOException {
        InputStream data = getClass().getResourceAsStream("/autumn.xml");
        this.witnessParser.parse( new InputStreamReader(data) );
        List<PsWitnessInfo> out = this.witnessParser.getWitnesses();
        Assert.assertTrue( out.size() == 5 );
        for ( PsWitnessInfo info : out  ) {
            System.out.println(info);
        }
    }
    
    @Test
    public void parseNestedWitnessList() throws ParserConfigurationException, SAXException, IOException {
        InputStream data = getClass().getResourceAsStream("/nested-witness.xml");
        this.witnessParser.parse( new InputStreamReader(data) );
        List<PsWitnessInfo> out = this.witnessParser.getWitnesses();
        
        int groupCnt = 0;
        for ( PsWitnessInfo info : out  ) {
            if ( info.hasGroupAlias() ) {
                groupCnt++;
                System.out.println("Grouped Witness: '"+info.getId()+"', group '"+info.getGroupId()+"'");
            } else {
                System.out.println(info);
            }
        }
        Assert.assertTrue( out.size() == 7 );
        Assert.assertTrue( groupCnt == 2 );
    }
}
