package org.juxtasoftware.diff;

import org.junit.Test;

import java.io.IOException;

/**
 * @author <a href="http://gregor.middell.net/" title="Homepage">Gregor Middell</a>
 */
public class DiffCollatorTest extends AbstractTest {

    @Test
    public void changeExample() throws IOException {
        print(collate(//
                comparand("that quick red fox died"),
                comparand("the quick ripe box lied")));
    }
    
    @Test
    public void punctuationTest() throws IOException {
        print(collate(//
                comparand(" 'Drink me,' but the wise little Alice was not going to do THAT in a hurry. 'No, I'll look first,'"),
                comparand(" 'drink me,' 'but I'll look first,'")));
    }
    
    @Test
    public void standardExample() throws IOException {
        print(collate(//
                comparand("quick red fox got rabies and died"),
                comparand("the quick red fox died")));
    }
    


    @Test
    public void emptyWitness() throws IOException {
        print(collate(//
                comparand(""),
                comparand("the quick red fox died")));
    }
}
