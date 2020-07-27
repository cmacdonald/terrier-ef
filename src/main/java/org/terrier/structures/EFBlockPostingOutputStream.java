package org.terrier.structures;
import it.cnr.isti.hpclab.ef.EliasFano;
import it.cnr.isti.hpclab.ef.structures.EFBlockIterablePosting;
import it.cnr.isti.hpclab.ef.structures.EFBlockLexiconEntry;
import it.cnr.isti.hpclab.ef.util.LongWordBitWriter;
import it.cnr.isti.hpclab.ef.util.SequenceEncoder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.terrier.structures.*;
import org.terrier.structures.postings.*;

public class EFBlockPostingOutputStream extends EFBasicPostingOutputStream  {
    
    // The sequence encoder to generate posting lists (positions)
    SequenceEncoder posAccumulator = new SequenceEncoder( DEFAULT_CACHE_SIZE, LOG2QUANTUM );

    LongWordBitWriter pos;
    long posOffset = 0;

    public EFBlockPostingOutputStream(String file_prefix) throws IOException {
        super(file_prefix);
        pos = new LongWordBitWriter(new FileOutputStream(file_prefix + EliasFano.POS_EXTENSION).getChannel(), ByteOrder.nativeOrder());
    }

    public Class<? extends IterablePosting> getPostingIteratorClass() {
        return EFBlockIterablePosting.class;
    }
    
    public Pointer writePostings(IterablePosting p, int postingLength, int maxFreq) throws IOException {
        docidsAccumulator.init( postingLength, num_docs, false, true, LOG2QUANTUM );
        freqsAccumulator.init( postingLength, maxFreq, true, false, LOG2QUANTUM );
        List<int[]> all_positions = new ArrayList<>();

        long sumMaxPos = 0; // in the first pass, we need to compute the upper bound to encode positions
        long occurrency = 0; // Do not trust le.getFrequency() because of block max limit!

        BlockPosting bp = (BlockPosting)p;
        
        long totalFreq = 0;
        long lastDocid = 0;
        while (p.next() != IterablePosting.END_OF_LIST) {
            assert p.getId() < num_docs;
            assert p.getFrequency() < maxFreq;
            docidsAccumulator.add( p.getId() - lastDocid );
            lastDocid = p.getId();
            freqsAccumulator.add(p.getFrequency());
            totalFreq += p.getFrequency();

            int[] positions = bp.getPositions();
            sumMaxPos += positions[(positions.length - 1)];
            occurrency += positions.length;
            all_positions.add(positions);
        }

        // After computing sumMaxPos, we re-scan the posting list to encode the positions
        posAccumulator.init(totalFreq, postingLength + sumMaxPos, true, false, LOG2QUANTUM );

        for (int[] positions : all_positions)
        {
            posAccumulator.add(1 + positions[0]);
            for (int i = 1; i < positions.length; i++)
                posAccumulator.add(positions[i] - positions[i-1]);
        }

        docidsOffset += docidsAccumulator.dump(docids);
        freqsOffset  += freqsAccumulator.dump(freqs);
        
        // Firstly we write decoding limits info
        posOffset += pos.writeGamma(posAccumulator.lowerBits());
        posOffset += posAccumulator.numberOfPointers() == 0 ? 0 : pos.writeNonZeroGamma( posAccumulator.pointerSize() );
        // Secondly we dump the EF representation of the position encoding
        posOffset += posAccumulator.dump(pos);

        return new EFBlockLexiconEntry(0, postingLength, 0, maxFreq, docidsOffset, freqsOffset, posOffset);
    }
}