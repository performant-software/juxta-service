package org.juxtasoftware.service;

import java.io.StringReader;

import org.junit.Before;
import org.junit.Test;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.dao.SourceDao;
import org.juxtasoftware.dao.WitnessDao;
import org.juxtasoftware.dao.WorkspaceDao;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.Source;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.model.Workspace;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Sets;

import eu.interedition.text.Range;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class ComparisonSetCollatorTest extends AbstractTest {
    private static final Logger LOG = LoggerFactory.getLogger(ComparisonSetCollatorTest.class);

    @Autowired private SourceDao sourceDao;
    @Autowired private WitnessDao witnessDao;
    @Autowired private ComparisonSetDao comparisonSetDao;
    @Autowired private Tokenizer tokenizer;
    @Autowired private ComparisonSetCollator collator;
    @Autowired private WorkspaceDao workspaceDao;
    @Autowired private AlignmentDao alignmentDao;
    
    @Before
    public void setup() {
        Workspace pub = this.workspaceDao.getPublic();
        if ( pub == null ) {
            pub = new Workspace();
            pub.setName("public");
            pub.setDescription("Default public workspace");
            Long id = this.workspaceDao.create(pub);
            pub.setId( id );
        }
    }
    
    @Test
    public void simpleCase() throws Exception {
        
        final Workspace pub = this.workspaceDao.getPublic();
        final String w1Contents = "The quick brown fox died.";
        final String w2Contents = "Quick red fox got rabies and died.";
        final String w3Contents = "The quick blue fox lives.";

        final Long srcId1 = this.sourceDao.create(pub, "w1.src", Source.Type.TXT, new StringReader(w1Contents));
        final Source src1 = this.sourceDao.find(pub.getId(), srcId1);
        
        final Long srcId2 = this.sourceDao.create(pub, "w2.src", Source.Type.TXT, new StringReader(w2Contents));
        final Source src2 = this.sourceDao.find(pub.getId(), srcId2);
        
        final Long srcId3 = this.sourceDao.create(pub, "w3.src", Source.Type.TXT, new StringReader(w3Contents));
        final Source src3 = this.sourceDao.find(pub.getId(), srcId3);

        final Witness w1 = new Witness();
        w1.setName("w1");
        w1.setSourceId(srcId2);
        w1.setText(src1.getText());
        w1.setWorkspaceId(pub.getId());
        w1.setId(witnessDao.create(w1));

        final Witness w2 = new Witness();
        w2.setName("w2");
        w2.setSourceId(src2.getId());
        w2.setText(src2.getText());
        w2.setWorkspaceId(pub.getId());
        w2.setId(witnessDao.create(w2));

        final Witness w3 = new Witness();
        w3.setName("w3");
        w3.setSourceId(src3.getId());
        w3.setText(src3.getText());
        w3.setWorkspaceId(pub.getId());
        w3.setId(witnessDao.create(w3));

        final ComparisonSet comparisonSet = new ComparisonSet();
        comparisonSet.setName("test");
        comparisonSet.setWorkspaceId(pub.getId());

        comparisonSetDao.create(comparisonSet);
        comparisonSetDao.addWitnesses(comparisonSet, Sets.newHashSet(w1, w2, w3));
        CollatorConfig cfg = this.comparisonSetDao.getCollatorConfig(comparisonSet);

        tokenizer.tokenize(comparisonSet, cfg, new BackgroundTaskStatus("tokenize"));
        collator.collate(comparisonSet, cfg, new BackgroundTaskStatus("collate"));
        
        LOG.debug("["+w2Contents+"] vs ["+w3Contents+"]");
        AlignmentConstraint constraint = new AlignmentConstraint(comparisonSet);
        constraint.addWitnessIdFilter(w2.getId());
        constraint.addWitnessIdFilter(w3.getId());
        for (Alignment align : this.alignmentDao.list(constraint) ) {
            final StringBuilder rowStr = new StringBuilder();
            AlignedAnnotation a = align.getWitnessAnnotation(w2.getId());
            rowStr.append("[").append(substringOf(w2Contents, a.getRange())).append("]");
            rowStr.append(" ").append(a.getRange());
            rowStr.append(" <===> ");
            a = align.getWitnessAnnotation(w3.getId());
            rowStr.append("[").append(substringOf(w3Contents, a.getRange())).append("]");
            rowStr.append(" ").append(a.getRange());
            LOG.debug(rowStr.toString());
        }
    }
    
    private String substringOf(String str, Range r) {
        return str.substring((int) r.getStart(), (int) r.getEnd());
    }
}
