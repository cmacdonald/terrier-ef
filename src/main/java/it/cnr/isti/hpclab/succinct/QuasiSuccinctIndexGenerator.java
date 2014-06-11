package it.cnr.isti.hpclab.succinct;

import it.cnr.isti.hpclab.succinct.structures.SuccinctDocumentIndex;
import it.cnr.isti.hpclab.succinct.structures.SuccinctLexiconEntry;
import it.cnr.isti.hpclab.succinct.util.LongWordBitWriter;
import it.cnr.isti.hpclab.succinct.util.SequenceEncoder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import org.terrier.structures.BitIndexPointer;
//import org.terrier.structures.FSADocumentIndex;
import org.terrier.structures.FSOMapFileLexiconOutputStream;
import org.terrier.structures.Index;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.LexiconOutputStream;
import org.terrier.structures.collections.FSOrderedMapFile;
import org.terrier.structures.postings.IterablePosting;
import org.terrier.structures.seralization.FixedSizeTextFactory;
import org.terrier.utility.ApplicationSetup;
import org.terrier.utility.Files;
import org.terrier.utility.TerrierTimer;

public class QuasiSuccinctIndexGenerator 
{
	public static int LOG2QUANTUM = 8;
	private static ByteOrder BYTEORDER = ByteOrder.nativeOrder();
	private static int DEFAULT_CACHE_SIZE = 64 * 1024;
	
	public static void link(String from, String to) throws IOException
	{
		Runtime.getRuntime().exec("ln -s " + from + " " + to);  
	}
	
	public static void main(final String args[]) throws IOException 
	{
		if (args.length != 3) {
			System.err.println("Usage: java it.cnr.isti.hpclab.succinct.QuasiSuccinctIndexGenerator <index.path> <src.index.prefix> <dst.index.prefix>");
			System.exit(-1);
		}

		String path = args[0];
		String srcPrefix = args[1];
		String dstPrefix = args[2];

		Index srcIndex;

		srcIndex = Index.createIndex(path, srcPrefix);
		if (Index.getLastIndexLoadError() != null) {
			System.err.println(Index.getLastIndexLoadError());
			System.exit(-2);
		}
		
		createLexiconDocidsFreqs(path, dstPrefix, srcIndex);
		createDocumentIndex(path, dstPrefix, srcIndex);
		createMetaIndex(path, dstPrefix, srcIndex);
		createProperties(path, dstPrefix, srcIndex);
	}

	private static void createProperties(final String path, final String dstPrefix, final Index srcIndex) throws IOException 
	{
		 Files.copyFile(srcIndex.getPath() + ApplicationSetup.FILE_SEPARATOR + srcIndex.getPrefix() + ".properties",
			 		    path               + ApplicationSetup.FILE_SEPARATOR + dstPrefix            + ".properties");
 
		 // Update new properties
		 String propertiesFilename = path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".properties";
		 Properties properties = new Properties();
		 properties.load(Files.openFileStream(propertiesFilename));
		 properties.setProperty("index.inverted.class", "it.cnr.isti.hpclab.succinct.structures.SuccinctInvertedIndex");
		 properties.setProperty("index.inverted.parameter_types", "org.terrier.structures.Index,java.lang.String,org.terrier.structures.DocumentIndex");
		 properties.setProperty("index.inverted.parameter_values", "index,structureName,document");
		 
		 properties.setProperty("index.lexicon-valuefactory.class", "it.cnr.isti.hpclab.succinct.structures.SuccinctLexiconEntry$Factory");
		 properties.setProperty("index.lexicon.bsearchshortcut", "default");
		 
		 properties.setProperty("index.document.class", "it.cnr.isti.hpclab.succinct.structures.SuccinctDocumentIndex");
		 properties.setProperty("index.document.parameter_types", "org.terrier.structures.Index");
		 properties.setProperty("index.document.parameter_values", "index");

		 
		 properties.setProperty("log2Quantum", Integer.toString(LOG2QUANTUM));
		 properties.setProperty("ByteOrder", BYTEORDER.toString());
		 
		 // This property disables the lookup for the meta-0.fsohashmap file that we never bring with new indexes
		 properties.setProperty("index.meta.reverse-key-names", "");
		 
		 properties.store(Files.writeFileStream(propertiesFilename),"");
  	}

	private static void createDocumentIndex(final String path, final String dstPrefix, final Index srcIndex) throws IOException 
	{
		//Files.copyFile(srcIndex.getPath() + ApplicationSetup.FILE_SEPARATOR + srcIndex.getPrefix() + ".document.fsarrayfile", path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".document.fsarrayfile");
		SuccinctDocumentIndex.write((org.terrier.structures.DocumentIndex) srcIndex.getDocumentIndex(), path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".sizes");
	}

	private static void createMetaIndex(final String path, final String dstPrefix, final Index srcIndex) throws IOException 
	{
		Files.copyFile(srcIndex.getPath() + ApplicationSetup.FILE_SEPARATOR + srcIndex.getPrefix() + ".meta.zdata", path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".meta.zdata");
		Files.copyFile(srcIndex.getPath() + ApplicationSetup.FILE_SEPARATOR + srcIndex.getPrefix() + ".meta.idx", path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".meta.idx");
	}

	@SuppressWarnings("resource")
	private static void createLexiconDocidsFreqs(final String path, final String dstPrefix, final Index srcIndex) throws IOException 
	{
		// The new lexicon writer (please note it is an output stream)
		LexiconOutputStream<String> los = new FSOMapFileLexiconOutputStream(path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".lexicon" + FSOrderedMapFile.USUAL_EXTENSION, new FixedSizeTextFactory(ApplicationSetup.MAX_TERM_LENGTH));
				
		int numberOfDocuments = srcIndex.getCollectionStatistics().getNumberOfDocuments();
		// The sequence encoder to generate posting lists (docids)
		SequenceEncoder docidsAccumulator = new SequenceEncoder( DEFAULT_CACHE_SIZE, LOG2QUANTUM );
		// The sequence encoder to generate posting lists (freqs)
		SequenceEncoder freqsAccumulator = new SequenceEncoder( DEFAULT_CACHE_SIZE, LOG2QUANTUM );

		// The new docids inverted file
		LongWordBitWriter docids = new LongWordBitWriter( new FileOutputStream(path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".docids").getChannel(), BYTEORDER );
		// The new freqs inverted file
		LongWordBitWriter freqs = new LongWordBitWriter( new FileOutputStream(path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".freqs").getChannel(), BYTEORDER );

		
		TerrierTimer tt = new TerrierTimer("", srcIndex.getCollectionStatistics().getNumberOfUniqueTerms());tt.start();    
		Iterator<Entry<String, LexiconEntry>> lexiconIterator = srcIndex.getLexicon().iterator();
				
		long docidsOffset = 0;
		long freqsOffset = 0;
		while (lexiconIterator.hasNext()) {
			// We get the next lexicon entry from the source index (assuming them ordered by termid)
			Map.Entry<String, LexiconEntry> leIn = lexiconIterator.next();
			LexiconEntry le = leIn.getValue();
				    	    	
			// We create the new lexicon entry with skip offset data included
			SuccinctLexiconEntry leOut = new SuccinctLexiconEntry( le.getTermId(), le.getDocumentFrequency(), le.getFrequency(), docidsOffset, freqsOffset);
				       	
			// We write the new lexicon entry to the new lexicon
			los.writeNextEntry(leIn.getKey(), leOut);
					 
			//if (leIn.getKey().equals("new"))
			//	System.err.println(freqsOffset);
			IterablePosting p = srcIndex.getInvertedIndex().getPostings((BitIndexPointer)le);
					 			
			docidsAccumulator.init( le.getDocumentFrequency(), numberOfDocuments, false, true, LOG2QUANTUM );
			freqsAccumulator.init(  le.getDocumentFrequency(), le.getFrequency(), true, false, LOG2QUANTUM );

			long lastDocid = 0;
			while (p.next() != IterablePosting.END_OF_LIST) {
				docidsAccumulator.add( p.getId() - lastDocid );
				lastDocid = p.getId();
				freqsAccumulator.add(p.getFrequency());
				//if (leIn.getKey().equals("attori"))
				//	System.err.print(p.getFrequency() + " ");
			}
			//if (leIn.getKey().equals("attori"))
			//	System.err.println();
						
			docidsOffset += docidsAccumulator.dump(docids);		
			freqsOffset  += freqsAccumulator.dump(freqs);
			
			tt.increment();
		}
		tt.finished();
				
		docidsAccumulator.close();
		docids.close();
		freqsAccumulator.close();
		freqs.close();
		los.close();
		srcIndex.close();
		
		// Files.copyFile(srcIndex.getPath() + ApplicationSetup.FILE_SEPARATOR + srcIndex.getPrefix() + ".lexicon.fsomaphash", path + ApplicationSetup.FILE_SEPARATOR + dstPrefix + ".lexicon.fsomaphash");
	}
}
