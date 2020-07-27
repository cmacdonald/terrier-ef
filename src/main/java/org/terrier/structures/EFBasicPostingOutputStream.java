package org.terrier.structures;
import it.cnr.isti.hpclab.ef.structures.EFBasicIterablePosting;
import it.cnr.isti.hpclab.ef.structures.EFLexiconEntry;
import it.cnr.isti.hpclab.ef.util.LongWordBitWriter;
import it.cnr.isti.hpclab.ef.util.SequenceEncoder;
import it.cnr.isti.hpclab.ef.EliasFano;
import java.nio.ByteOrder;

import org.terrier.structures.*;
import org.terrier.structures.postings.*;
import java.io.IOException;
import java.io.FileOutputStream;


public class EFBasicPostingOutputStream extends AbstractPostingOutputStream {

    protected static final int DEFAULT_CACHE_SIZE = 64 * 1024 * 1024;

    
    protected int LOG2QUANTUM = Integer.parseInt(System.getProperty(EliasFano.LOG2QUANTUM, "8"));
    LongWordBitWriter           docids;
    LongWordBitWriter           freqs;
    // The sequence encoder to generate posting lists (docids)
    SequenceEncoder docidsAccumulator = new SequenceEncoder( DEFAULT_CACHE_SIZE, LOG2QUANTUM );
    // The sequence encoder to generate posting lists (freqs)
    SequenceEncoder freqsAccumulator = new SequenceEncoder( DEFAULT_CACHE_SIZE, LOG2QUANTUM );
    
    long docidsOffset = 0;
    long freqsOffset = 0;

    int num_docs;

    
    public EFBasicPostingOutputStream(String filename_prefix) throws IOException {
        super();
        docids = new LongWordBitWriter(new FileOutputStream(filename_prefix).getChannel(), ByteOrder.nativeOrder());
        freqs  = new LongWordBitWriter(new FileOutputStream(filename_prefix).getChannel(), ByteOrder.nativeOrder());    
	}

	public Pointer getOffset() { return null; }	
	public int getLastDocidWritten() {
        return -1;
    }

    public void close() {}
    
    public Class<? extends IterablePosting> getPostingIteratorClass() {
        return EFBasicIterablePosting.class;
    }
    
	public Pointer writePostings(IterablePosting p, int postingLength, int maxFreq) throws IOException {
        docidsAccumulator.init( postingLength, num_docs, false, true, LOG2QUANTUM );
        freqsAccumulator.init( postingLength, maxFreq, true, false, LOG2QUANTUM );
            
        long lastDocid = 0;
        while (p.next() != IterablePosting.END_OF_LIST) {

            assert p.getId() < num_docs;
            assert p.getFrequency() < maxFreq;

            docidsAccumulator.add( p.getId() - lastDocid );
            lastDocid = p.getId();
            freqsAccumulator.add(p.getFrequency());
        }

        docidsOffset += docidsAccumulator.dump(docids);
        freqsOffset  += freqsAccumulator.dump(freqs);
        
        return new EFLexiconEntry(0, postingLength, 0, maxFreq, docidsOffset, freqsOffset);
    }

}