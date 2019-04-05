/*
 * Elias-Fano compression for Terrier 5
 *
 * Copyright (C) 2018-2018 Nicola Tonellotto 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */
package it.cnr.isti.hpclab.ef;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.terrier.applications.BatchIndexing;
import org.terrier.applications.TRECIndexingSinglePass;
import org.terrier.structures.Index;
import org.terrier.utility.ApplicationSetup;
import org.terrier.utility.Files;

/** 
 * Base class for a test that requires ApplicationSetup to be correctly initialised.
 * Uses a JUnit-created temporary folder, and invokes TRECSetup on it, to ensure that
 * a default configuration is generated.
 */
public class EFSetupTest 
{
	@Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

	protected String terrierHome;
	protected String terrierEtc;
	
	@Before
	public void makeEnvironment() throws Exception
	{
		// Setting up terrier.home to user home folder
		this.terrierHome = System.getProperty("user.dir");
		System.setProperty("terrier.home", terrierHome);
		System.out.println("terrier.home = "+ terrierHome);
		
		// Setting up terrier.etc to a tmp directory
		this.terrierEtc = tmpFolder.getRoot().toString();
		System.setProperty("terrier.etc", terrierEtc);
		System.out.println("terrier.etc = "+ terrierEtc);
		
		// Cleaning up the contents of terrier.etc if it exists
		File fs[] = new File(terrierEtc).listFiles();
		if (fs != null)
			for (File f : fs)
				f.delete();
		// Creating terrierEtc
		new File(terrierEtc).mkdirs();

		
		System.setProperty("terrier.setup", terrierEtc + "/terrier.properties");
		createTerrierLogFile();
		createTerrierPropertiesFile();
		
		InputStream is = new FileInputStream(terrierEtc + "/terrier.properties");
		Properties prop = new Properties();
		prop.load(is);
		is.close();
		
		OutputStream os = new FileOutputStream(terrierEtc+ "/terrier.properties.test");
		prop.setProperty("terrier.index.path", terrierEtc);
		prop.setProperty("trec.results", terrierEtc);
		
		// addGlobalTerrierProperties(prop);
		prop.store(os, "#generated by " + this.getClass().getName() + " and " + EFSetupTest.class.getName());
		os.close();
		assertTrue(Files.delete(terrierEtc + "/terrier.properties"));
		assertTrue(Files.rename(terrierEtc+ "/terrier.properties.test", terrierEtc + "/terrier.properties"));		
		
		ApplicationSetup.bootstrapInitialisation();

		assertEquals(terrierHome, ApplicationSetup.TERRIER_HOME);
		assertEquals(terrierEtc,  ApplicationSetup.TERRIER_ETC);
		assertEquals(terrierEtc,  ApplicationSetup.TERRIER_INDEX_PATH);
		
		ApplicationSetup.setProperty("stopwords.filename", System.getProperty("user.dir") + "/src/test/resources/stopword-list.txt");
		ApplicationSetup.setProperty("termpipelines", "Stopwords,PorterStemmer");
		
	}
	
	@After 
	public void deleteTerrierEtc()
	{
		File fs[] = new File(terrierEtc).listFiles();
		if (fs != null)
			for(File f : fs)
				f.delete();
		org.terrier.utility.ApplicationSetup.clearAllProperties();
	}

	
	private void createTerrierLogFile() throws IOException
	{
		// creating a terrier-log.xml file
		PrintWriter terrierlog = new PrintWriter(new FileWriter(terrierEtc + File.separator + "terrier-log.xml"));
		System.out.println("Creating logging configuration (terrier-log.xml) file.");
		terrierlog.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
		terrierlog.println("<!DOCTYPE log4j:configuration SYSTEM \"log4j.dtd\">");
		terrierlog.println("<log4j:configuration xmlns:log4j=\"http://jakarta.apache.org/log4j/\">");
		terrierlog.println(" <appender name=\"console\" class=\"org.apache.log4j.ConsoleAppender\">");
		terrierlog.println("  <param name=\"Target\" value=\"System.err\"/>");
		terrierlog.println("  <layout class=\"org.apache.log4j.SimpleLayout\"/>");
		terrierlog.println(" </appender>");
		terrierlog.println(" <root>");
		//if (debug)
			terrierlog.println("  <priority value=\"debug\" /><!-- Terrier: change debug to info to get less output -->");
		//else
		//	terrierlog.println("  <priority value=\"info\" /><!-- Terrier: change to debug to get more output -->");
		terrierlog.println("  <appender-ref ref=\"console\" />");
		terrierlog.println(" </root>");
		terrierlog.println("</log4j:configuration>");
		terrierlog.close();
		
		System.out.println("Terrier log file created: " + terrierEtc + File.separator + "terrier-log.xml");
	}
	
	private void createTerrierPropertiesFile() throws IOException
	{
		// creating the terrier.properties file
		PrintWriter propertiesWriter = new PrintWriter(new FileWriter(terrierEtc + File.separator + "terrier.properties"));
		System.out.println("Creating terrier.properties file.");		
		propertiesWriter.println("#document tags specification");
		propertiesWriter.println("#for processing the contents of");
		propertiesWriter.println("#the documents, ignoring DOCHDR");
		propertiesWriter.println("TrecDocTags.doctag=DOC");
		propertiesWriter.println("TrecDocTags.idtag=DOCNO");
		propertiesWriter.println("TrecDocTags.skip=DOCHDR");

		propertiesWriter.println("#set to true if the tags can be of various case");
		propertiesWriter.println("TrecDocTags.casesensitive=false");

		propertiesWriter.println("#stop-words file");
		propertiesWriter.println("stopwords.filename=stopword-list.txt");

		propertiesWriter.println("#the processing stages a term goes through");
		propertiesWriter.println("termpipelines=Stopwords,PorterStemmer");
		
		propertiesWriter.close();
		
		System.out.println("Terrier properties file created: " + terrierEtc + File.separator + "terrier.properties");
	}
	
	protected void doShakespeareIndexing() throws Exception
	{
		makeCollectionSpec(new PrintWriter(Files.writeFileWriter(ApplicationSetup.TERRIER_ETC +  "/collection.spec")));
		doIndexing();
	}
	
	protected void makeCollectionSpec(PrintWriter p) throws Exception 
	{
		p.println(System.getProperty("user.dir") + "/src/test/resources/shakespeare/shakespeare-merchant.trec.1");
		p.println(System.getProperty("user.dir") + "/src/test/resources/shakespeare/shakespeare-merchant.trec.2");
		p.close();
	}

	protected void doIndexing() throws Exception
	{
		String path = ApplicationSetup.TERRIER_INDEX_PATH;
		String prefix = ApplicationSetup.TERRIER_INDEX_PREFIX;
		
		BatchIndexing indexing = new TRECIndexingSinglePass(ApplicationSetup.TERRIER_INDEX_PATH, ApplicationSetup.TERRIER_INDEX_PREFIX);
		indexing.index();			
		
//		TrecTerrier.main(new String[] {"-i", "-j"});
		
		//check that application setup hasnt changed unexpectedly
		assertEquals(path, ApplicationSetup.TERRIER_INDEX_PATH);
		assertEquals(prefix, ApplicationSetup.TERRIER_INDEX_PREFIX);
		
		//check that indexing actually created an index
		assertTrue("Index does not exist at ["+ApplicationSetup.TERRIER_INDEX_PATH+","+ApplicationSetup.TERRIER_INDEX_PREFIX+"]", Index.existsIndex(ApplicationSetup.TERRIER_INDEX_PATH, ApplicationSetup.TERRIER_INDEX_PREFIX));
		Index i = Index.createIndex();
		assertNotNull(Index.getLastIndexLoadError(), i);
		assertTrue("Index does not have an inverted structure", i.hasIndexStructure("inverted"));
		assertTrue("Index does not have an lexicon structure", i.hasIndexStructure("lexicon"));
		assertTrue("Index does not have an document structure", i.hasIndexStructure("document"));
		assertTrue("Index does not have an meta structure", i.hasIndexStructure("meta"));
		i.close();
	}	
}
