package edu.uw.bothell.rdf.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.jrdf.graph.Graph;
import org.jrdf.query.answer.Answer;
import org.jrdf.query.answer.TypeValue;
import org.jrdf.util.ClosableIterator;
import org.junit.Before;
import org.junit.Test;

import edu.uw.bothell.rdf.beans.RdfBean;


public class AggregateSearcherTest {

	protected AggregateSearcher searcher;
	
	@Before
	public void setUp() {
		
		searcher = new AggregateSearcher();
	}
	
	@Test
	public void testAddSource() throws IOException, SearcherException {
		
		Graph g = searcher.getGraph();
		assertTrue(g.isEmpty());
		
		loadTestFile();
		
		/*
		 * Test that the graph is not empty. This is about as much as 
		 * we can do right now. 
		 */
		assertFalse(g.isEmpty());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testAddSourceWithNullUrl() throws IOException, SearcherException {
		
		URL url = null;
		searcher.addSource(url);
	}
	
	@Test
	public void testSearchWithNoTriples() throws IOException, SearcherException {
			
		List<RdfBean> triples = searcher.search();
		assertNotNull(triples);
		
		assertEquals(0, triples.size());
	}
	
	@Test
	public void testSearch() throws IOException, SearcherException {
		
		loadTestFile();
		
		List<RdfBean> triples = searcher.search();
		assertNotNull(triples);
		
		assertEquals(9, triples.size());
	}
	
	@Test
	public void testSearchSparql() throws IOException, SearcherException {
		
		loadTestFile();
		
		Answer a = searcher.search("SELECT ?object WHERE { <http://www.youtube.com/watch?v=HeUrEh-nqtU> <http://localhost#tag> ?object . }");
		assertNotNull(a);
		
		ClosableIterator<TypeValue[]> iter = a.columnValuesIterator();
		int count = 0;
		while (iter.hasNext()) {
			iter.next();
			count++;
		}
		
		assertEquals(5, count);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSearchSparqlWithNullQuery() {
		searcher.search(null);
	}
	
	@Test
	public void testSearchSparqlPredicateObject() throws IOException, SearcherException {
		
		loadTestFile();
		
		List<RdfBean> beans = searcher.search("http://localhost#type", "Screencast");
		assertNotNull(beans);
		assertEquals(1, beans.size());
		
		RdfBean bean = beans.get(0);
		assertEquals("http://www.youtube.com/watch?v=QEAbCKPZkD4", bean.getSubject());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSearchSparqlPredicateObjectWithNullPredicate() throws SearcherException {
		
		searcher.search(null, "adf");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSearchSparqlPredicateObjectWithEmptyPredicate() throws SearcherException {
		searcher.search("", "adsf");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSearchSparqlPredicateObjectWithNullObject() throws SearcherException {
		searcher.search("adsf", null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSearchSparqlPredicateObjectWithEmptyObject() throws SearcherException {
		searcher.search("adsf", "");
	}
	
	@Test
	public void testFindBySubject() throws IOException, SearcherException {
		
		loadTestFile();
		
		String subject = "http://www.youtube.com/watch?v=QEAbCKPZkD4";
		RdfBean bean = searcher.findBySubject(subject);
		assertNotNull(bean);
		assertEquals(subject, bean.getSubject());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testFindBySubjectWithNullParam() throws SearcherException {
		searcher.findBySubject(null);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testFindBySubjectWithEmptyParam() throws SearcherException {
		searcher.findBySubject("");
	}
	
	protected void loadTestFile() throws IOException, SearcherException {
		InputStream in = ClassLoader.getSystemResourceAsStream("km-video-lib-v3.xml");
		searcher.addSource(in, "http://localhost/test");
	}
}
