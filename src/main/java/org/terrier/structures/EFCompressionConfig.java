package org.terrier.structures;
import java.io.IOError;
import java.io.IOException;
import java.util.Iterator;
import org.terrier.structures.indexing.CompressionFactory.CompressionConfiguration;
import org.terrier.structures.seralization.FixedSizeWriteableFactory;
import org.terrier.structures.postings.IterablePosting;
import it.cnr.isti.hpclab.ef.structures.*;
public class EFCompressionConfig extends CompressionConfiguration {
    
    public EFCompressionConfig(String structureName, String[] fields,int blocks,int maxblocks) {
        super(structureName, fields, blocks, maxblocks);
        if (fields.length > 0)
        {
            throw new IllegalArgumentException("Fields are not supported by " + this.getClass().getName());
        }
    }

    /** Write a file of postings to the given location */
    @Override
    public AbstractPostingOutputStream getPostingOutputStream(String filename)
    {
        try{
            if (this.hasBlocks > 0)
                return new EFBlockPostingOutputStream(filename);
            return new EFBasicPostingOutputStream(filename);
        } catch (IOException ioe) {
            throw new IOError(ioe);
        }
    }

    /** What is the posting iterator class for this structure */
    @Override
    protected Class<? extends IterablePosting> getPostingIteratorClass() {
        if (this.hasBlocks > 0)
            return EFBlockIterablePosting.class;
        return EFBasicIterablePosting.class;
    }

    /** What is the structure class for this structure */
    @Override
    protected  Class<? extends PostingIndex<?>> getStructureClass() {
        return EFInvertedIndex.class;
    }
    
    /** What is the input stream class for this structure */
    @Override
    protected Class<? extends Iterator<IterablePosting>> getStructureInputStreamClass() {
        return null;
    }

    /** What is the file extension for this structure. Usually ".bf" for BitFile and ".if" for files containing compressed integers */
    @Override
    public String getStructureFileExtension() {
        return ".ef";
    }

    @Override
    public FixedSizeWriteableFactory<Pointer> getPointerFactory() {
        throw new UnsupportedOperationException("EF indices must be accesed via an EF LexiconEntry");
    }

    @Override
    public FixedSizeWriteableFactory<LexiconEntry> getLexiconEntryFactory() {
        if (this.hasBlocks > 0)
            return new EFBlockLexiconEntry.Factory();
        return new EFLexiconEntry.Factory();
    }

    @Override
    public FixedSizeWriteableFactory<DocumentIndexEntry> getDocumentIndexEntryFactory() {
        return new SimpleDocumentIndexEntry.Factory();
    }
}