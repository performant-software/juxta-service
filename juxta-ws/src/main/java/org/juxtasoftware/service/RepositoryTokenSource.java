package org.juxtasoftware.service;

import static eu.interedition.text.query.Criteria.and;
import static eu.interedition.text.query.Criteria.annotationName;
import static eu.interedition.text.query.Criteria.or;
import static eu.interedition.text.query.Criteria.rangeOverlap;
import static eu.interedition.text.query.Criteria.text;
import static org.juxtasoftware.Constants.TOKEN_NAME;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.regex.Pattern;

import org.juxtasoftware.diff.Token;
import org.juxtasoftware.diff.TokenSource;
import org.juxtasoftware.diff.impl.SimpleToken;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import eu.interedition.text.Annotation;
import eu.interedition.text.AnnotationRepository;
import eu.interedition.text.Range;
import eu.interedition.text.Text;
import eu.interedition.text.TextRepository;
import eu.interedition.text.query.Operator;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class RepositoryTokenSource implements TokenSource {
    private static final Pattern PUNCTUATION = Pattern.compile("[\\p{Pd}\\p{Po}]");

    private final TokenizerConfiguration config;
    private final AnnotationRepository annotationRepository;
    private final TextRepository textRepository;

    public RepositoryTokenSource(TokenizerConfiguration config, AnnotationRepository annotationRepository, TextRepository textRepository) {
        this.config = config;
        this.annotationRepository = annotationRepository;
        this.textRepository = textRepository;
    }

    @Override
    public List<Token> tokensOf(Text text, Set<Range> ranges) throws IOException {
        final Operator criterion = and(text(text), annotationName(TOKEN_NAME));
        if (!ranges.isEmpty()) {
            final Operator rangeCriterion = or();
            for (Range range : ranges) {
                rangeCriterion.add(rangeOverlap(range));
            }
            criterion.add(rangeCriterion);
        }

        final Map<Range, Annotation> tokenIndex = Maps.uniqueIndex(annotationRepository.find(criterion), new Function<Annotation, Range>() {
            @Override
            public Range apply(Annotation input) {
                return input.getRange();
            }
        });

        final SortedMap<Range, String> tokenTexts = textRepository.bulkRead(text, Sets.newTreeSet(tokenIndex.keySet()));
        final List<Token> tokens = Lists.newArrayListWithExpectedSize(tokenTexts.size());
        for (Map.Entry<Range, String> tokenEntry : tokenTexts.entrySet()) {
            String tokenText = tokenEntry.getValue();
            if (config.isFilterWhitespace()) {
                tokenText = tokenText.trim();
            }
            if (config.isFilterCase()) {
                tokenText = tokenText.toLowerCase();
            }
            if (config.isFilterPunctuation()) {
                tokenText = PUNCTUATION.matcher(tokenText).replaceAll("");
            }
            tokens.add(new SimpleToken(tokenIndex.get(tokenEntry.getKey()), tokenText));
        }
        return tokens;
    }
}
