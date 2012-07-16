package org.juxtasoftware.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;

import org.juxtasoftware.Constants;
import org.juxtasoftware.dao.AlignmentDao;
import org.juxtasoftware.dao.ComparisonSetDao;
import org.juxtasoftware.diff.Comparison;
import org.juxtasoftware.diff.DiffCollator;
import org.juxtasoftware.diff.DiffCollatorConfiguration;
import org.juxtasoftware.diff.Difference;
import org.juxtasoftware.diff.DifferenceStore;
import org.juxtasoftware.diff.Token;
import org.juxtasoftware.diff.TokenSource;
import org.juxtasoftware.diff.TranspositionSource;
import org.juxtasoftware.diff.impl.SimpleTokenComparator;
import org.juxtasoftware.model.Alignment;
import org.juxtasoftware.model.Alignment.AlignedAnnotation;
import org.juxtasoftware.model.AlignmentConstraint;
import org.juxtasoftware.model.CollatorConfig;
import org.juxtasoftware.model.CollatorConfig.HyphenationFilter;
import org.juxtasoftware.model.ComparisonSet;
import org.juxtasoftware.model.JuxtaAnnotation;
import org.juxtasoftware.model.QNameFilter;
import org.juxtasoftware.model.Witness;
import org.juxtasoftware.util.BackgroundTaskSegment;
import org.juxtasoftware.util.BackgroundTaskStatus;
import org.juxtasoftware.util.QNameFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import eu.interedition.text.Annotation;
import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Name;
import eu.interedition.text.NameRepository;
import eu.interedition.text.Range;
import eu.interedition.text.TextRepository;
import eu.interedition.text.rdbms.RelationalAnnotation;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ComparisonSetCollator extends DiffCollator {
    @Autowired private QNameFilters filters;
    @Autowired private ComparisonSetDao setDao;
    @Autowired private AnnotationRepository annotationRepository;
    @Autowired private AlignmentDao alignmentDao;
    @Autowired private TextRepository textRepository;
    @Autowired private NameRepository nameRepository;
    @Autowired private Integer collationBatchSize;
    
    private Set<Witness> witnessList;
    private ComparisonSet comparisonSet;
    
    private static final Logger LOG = LoggerFactory.getLogger( ComparisonSetCollator.class.getName());

    public void collate(ComparisonSet comparisonSet, CollatorConfig config, BackgroundTaskStatus taskStatus) throws IOException {

        // First, remove any pre-existing alignment data
        this.alignmentDao.clear(comparisonSet);
        
        // grab reference to key data used in the colaltion
        this.comparisonSet = comparisonSet;
        this.witnessList = this.setDao.getWitnesses(comparisonSet);
        
        // copy the witness list into a working copy
        final Set<Witness> witnesses = new HashSet<Witness>( this.witnessList );
        final CollatorConfigAdapter configAdapter = new CollatorConfigAdapter(config);

        final BackgroundTaskSegment ts = taskStatus.add(1, new BackgroundTaskSegment((witnesses.size() * (witnesses.size() - 1)) / 2));
        taskStatus.setNote("Collating SET " + comparisonSet);
        LOG.info("Collating " + comparisonSet);

        for (Iterator<Witness> baseIt = witnesses.iterator(); baseIt.hasNext(); ) {
            final Witness base = baseIt.next();
            baseIt.remove();

            for (Witness witness : witnesses) {
                taskStatus.setNote(base + " vs. " + witness);
                LOG.info("Collating: " + base + " vs. " + witness);

                collate(configAdapter, base, witness);
                configAdapter.getDifferenceStore().save();
                ts.incrementValue();
            }
        }
    }

    private class CollatorConfigAdapter implements DiffCollatorConfiguration, TokenizerConfiguration, TranspositionSource {

        private final CollatorConfig config;
        private final Comparator<Token> tokenComparator;
        private final TokenSource tokenSource;
        private final MemoryDiffStore memAlignStore = new MemoryDiffStore();

        private CollatorConfigAdapter(CollatorConfig config) {
            this.config = config;
            this.tokenComparator = new SimpleTokenComparator();
            this.tokenSource = new RepositoryTokenSource(this, annotationRepository, textRepository);
        }

        @Override
        public TokenSource getTokenSource() {
            return tokenSource;
        }

        @Override
        public TranspositionSource getTranspositionSource() {
            return this;
        }

        @Override
        public Comparator<Token> getTokenComparator() {
            return tokenComparator;
        }

        @Override
        public DifferenceStore getDifferenceStore() {
            return memAlignStore;
        }

        @Override
        public boolean isFilterWhitespace() {
            return config.isFilterWhitespace();
        }

        @Override
        public boolean isFilterPunctuation() {
            return config.isFilterPunctuation();
        }

        @Override
        public boolean isFilterCase() {
            return config.isFilterCase();
        }
        
        @Override
        public HyphenationFilter getHyphenationFilter() {
            return this.config.getHyphenationFilter();
        }

        @Override
        public Set<Set<Annotation>> transpositionsIn(Comparison collation) throws IOException {
            
            // The comparands here are always instances of a juxta Witness
            Witness base = (Witness)collation.getBase();
            Witness witness = (Witness)collation.getWitness();
            
            // get a list of all tranpositions in this set
            final AlignmentConstraint constraint = new AlignmentConstraint(ComparisonSetCollator.this.comparisonSet, base.getId());
            final QNameFilter transposeFilter = ComparisonSetCollator.this.filters.getTranspositionsFilter();
            constraint.setFilter( transposeFilter );
            final Set<Set<Annotation>> transpositions = Sets.newHashSet();
            for ( Alignment align :ComparisonSetCollator.this.alignmentDao.list(constraint)) {
                boolean transpositionAdded = false;
                for  (AlignedAnnotation anno : align.getAnnotations()) {
                    if ( anno.getWitnessId().equals(witness.getId()) == false ) {
                        continue;
                    }
                    if (collation.getWitnessRanges().isEmpty()) {
                        transpositions.add(  toAnnotations(align.getAnnotations()) );
                        transpositionAdded = true;
                        break;
                    }
                    for (Range witnessRange : collation.getWitnessRanges()) {
                        if (witnessRange.hasOverlapWith(anno.getRange())) {
                            transpositions.add(  toAnnotations(align.getAnnotations()) );
                            transpositionAdded = true;
                            break;
                        }
                    }
                    if (transpositionAdded) {
                        break;
                    }
                }
                
            }
            return transpositions;
        }
        
        private Set<Annotation> toAnnotations( List<AlignedAnnotation> data ) {
            Set<Annotation> out = new HashSet<Annotation>();
            for ( AlignedAnnotation a : data ) {
                for (Witness w : ComparisonSetCollator.this.witnessList ) {
                    if ( w.getId().equals(a.getWitnessId())) {
                        out.add( new RelationalAnnotation(w.getText(), a.getQName(), a.getRange(), null, a.getId()) );
                        break;
                    }
                }
            }
            if (out.size() != 2) {
                throw new RuntimeException("BAD THINGS");
            }
            return out;
        }
    }
    
    /**
     * An alignment store that caches in-progress collation alignemts in
     * memory. Once a threshold of alignments have been collected, they are
     * dumped in bulk to the db.
     * 
     * @author loufoster
     *
     */
    private final class MemoryDiffStore implements DifferenceStore {
        protected List<Difference> differences = new LinkedList<Difference>();
        protected Name addDelName;
        protected Name changeName;
        
        public MemoryDiffStore() {
            this.addDelName = nameRepository.get(Constants.ADD_DEL_NAME);
            this.changeName = nameRepository.get(Constants.CHANGE_NAME);
        }
        
        @Override
        public void add(Difference aignment) throws IOException {
            this.differences.add(aignment);
            if ( differences.size() >= collationBatchSize ) {
                save();
            }
        }
        
        private Long findWitnessId(Annotation a) {
            for ( Witness w : ComparisonSetCollator.this.witnessList) {
                if ( w.getText().equals(a.getText()) ) {
                    return w.getId();
                }
            }
            LOG.error("No witness found for annotaion "+a.toString());
            return null;
        }
        
        @Override
        public void save() throws IOException {
            // run thru all resulting alignments and look for gaps
            // and stuff them in a map of gap annotations to be created.
            SortedMap<Long, Annotation>  newBaseGaps = Maps.newTreeMap();
            SortedMap<Long, Annotation>  newWitnessGaps = Maps.newTreeMap();
            for (Difference a : this.differences ) {
                final Annotation base = a.getBase();
                final Annotation witness = a.getWitness();
                if (base instanceof GapAnnotation) {
                    final long offset = ((GapAnnotation) base).getOffset();
                    if ( !newBaseGaps.containsKey(offset)) {
                        newBaseGaps.put(offset, base);
                    }
                } else if (witness instanceof GapAnnotation) {
                    final long offset = ((GapAnnotation) witness).getOffset();
                    if ( !newWitnessGaps.containsKey(offset)) {
                        newWitnessGaps.put(offset, witness);
                    }
                }
            }

            // create all gap annotations in the repo. This transforms the gap into a RelationalAnnotation
            // that is pushed into the gap maps.
            final SortedMap<Long, Annotation> baseGaps = Maps.newTreeMap();
            final SortedMap<Long, Annotation> witnessGaps = Maps.newTreeMap();
            baseGaps.putAll(Maps.uniqueIndex(annotationRepository.create(newBaseGaps.values()), GapAnnotation.TO_OFFSET));
            witnessGaps.putAll(Maps.uniqueIndex(annotationRepository.create(newWitnessGaps.values()), GapAnnotation.TO_OFFSET));
            newBaseGaps.clear();
            newBaseGaps = null;
            newWitnessGaps.clear();
            newWitnessGaps = null;

            // run thru all alignments AGAIN and substitue the new annotations
            // for any gap. Convert the results to juxta alignments. Remove each
            // diff and gap as the process goes along rather than keeping multple copies
            List<Alignment> alignments = new ArrayList<Alignment>(this.differences.size());
            while ( this.differences.size() > 0 ) {
                Difference diff = this.differences.remove(0);
                
                Annotation base = diff.getBase();
                final Range baseRange = base.getRange();
                if ( baseRange.length() == 0 ) {
                    base = baseGaps.get(baseRange.getEnd());
                }

                Annotation witness = diff.getWitness();
                final Range witnessRange = witness.getRange();
                if ( witnessRange.length() == 0 ) {
                    witness = witnessGaps.get(witnessRange.getEnd());
                }
                
                // convert to alignment and add to list
                Name name = this.changeName;
                if ( diff.getType().equals(Difference.Type.ADD_DEL)) {
                    name = this.addDelName;
                }
                JuxtaAnnotation jxBase = new JuxtaAnnotation(findWitnessId(base), base.getText(), 
                    base.getName(), base.getRange(), ((RelationalAnnotation)base).getId());
                JuxtaAnnotation jxWitness = new JuxtaAnnotation(findWitnessId(witness), witness.getText(), 
                    witness.getName(), witness.getRange(), ((RelationalAnnotation)witness).getId());
                Alignment align =  new Alignment(comparisonSet.getId(), diff.getGroup(), name,  
                    jxBase, jxWitness, diff.getEditDistance());
                alignments.add( align );
            }
            
            // create the batch of alignments
            int created =0;
            try {
                created = ComparisonSetCollator.this.alignmentDao.create(alignments);
            } catch (Exception e) {
                LOG.error("Error creating alignments", e);
            }
            if ( created != alignments.size() ) {
                LOG.error("Unable to create entries for all alignments. Expected count: "
                    +alignments.size()+", Actual: "+created);
            }
            
            // wipe out the cached data to be ready for the next round
            alignments.clear();
            alignments = null;
            this.differences.clear();
        }
    }
}
