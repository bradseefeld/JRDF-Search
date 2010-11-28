package edu.uw.bothell.rdf.search;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.log4j.Logger;
import org.jrdf.JRDFFactory;
import org.jrdf.SortedMemoryJRDFFactory;
import org.jrdf.collection.MemMapFactory;
import org.jrdf.graph.AnyObjectNode;
import org.jrdf.graph.AnyPredicateNode;
import org.jrdf.graph.AnySubjectNode;
import org.jrdf.graph.Graph;
import org.jrdf.graph.GraphElementFactory;
import org.jrdf.graph.GraphElementFactoryException;
import org.jrdf.graph.Resource;
import org.jrdf.graph.Triple;
import org.jrdf.parser.ParseException;
import org.jrdf.parser.Parser;
import org.jrdf.parser.StatementHandlerException;
import org.jrdf.parser.rdfxml.GraphRdfXmlParser;
import org.jrdf.query.answer.Answer;
import org.jrdf.query.answer.TypeValue;
import org.jrdf.util.ClosableIterator;
import org.jrdf.util.EscapeURL;

import edu.uw.bothell.rdf.beans.RdfBean;

/**
 * Searches through multiple RDF sources such that search results look like they
 * all came from one place. Duplicates are not merged. Abstracts out the JRDF
 * library and (hopefully) any of the caveats to working with RDF resources.
 * 
 * @author bseefeld
 */
public class AggregateSearcher {

	/**
	 * The internal graph structure of all the RDF resources.
	 */
	protected Graph rdfGraph;

	/**
	 * Used to parse RDF resources.
	 */
	protected Parser parser;

	protected JRDFFactory jrdfFactory;

	/** Internal class level constructor. */
	private static final Logger LOG = Logger.getLogger(AggregateSearcher.class);

	/**
	 * Default constructor.
	 */
	public AggregateSearcher() {

		jrdfFactory = SortedMemoryJRDFFactory.getFactory();
		rdfGraph = jrdfFactory.getGraph();

		parser = new GraphRdfXmlParser(rdfGraph, new MemMapFactory());
	}

	/**
	 * Get the internal graph structure.
	 * 
	 * @return
	 */
	public Graph getGraph() {
		return rdfGraph;
	}

	/**
	 * Get the number of triples currently in the graph.
	 * 
	 * @return
	 */
	public int size() {
		return (int) rdfGraph.getNumberOfTriples();
	}

	/**
	 * Do a blind search on the RDF graph. This essentially returns *all* RDF
	 * triples.
	 * 
	 * @return
	 */
	public List<RdfBean> search() {

		ClosableIterator<Triple> iter = rdfGraph.find(AnySubjectNode.ANY_SUBJECT_NODE,
				AnyPredicateNode.ANY_PREDICATE_NODE,
				AnyObjectNode.ANY_OBJECT_NODE).iterator();
		
		return collapse(iter);
	}

	/**
	 * Perform a raw SPARQL query. This method is not recommended since it will
	 * return results in a non-normalized form.
	 * 
	 * TODO: Should this be protected scope instead?
	 * 
	 * @param sparql
	 * @return
	 */
	public Answer search(String sparql) {

		if (sparql == null || sparql.isEmpty()) {
			throw new IllegalArgumentException(
					"SPARQL query cannot be null or empty.");
		}

		Answer a = jrdfFactory.getNewSparqlConnection().executeQuery(rdfGraph,
				sparql);
		return a;
	}

	public List<RdfBean> search(String predicate, String object) throws SearcherException {

		if (predicate == null || predicate.isEmpty()) {
			throw new IllegalArgumentException(
					"Predicate cannot be null or empty.");
		}

		if (object == null || object.isEmpty()) {
			throw new IllegalArgumentException(
					"Object cannot be null or empty.");
		}

		StringBuilder sparql = new StringBuilder();
		sparql.append("SELECT ?subject WHERE { ?subject <");
		sparql.append(predicate);
		sparql.append("> \"");
		sparql.append(object);
		sparql.append("\" . }");
		
		Answer answer = search(sparql.toString());
		if (answer == null) {
			return new LinkedList<RdfBean>();
		}
		
		List<RdfBean> rdfBeans = new LinkedList<RdfBean>();
		ClosableIterator<TypeValue[]> iter = answer.columnValuesIterator();
		while (iter.hasNext()) {
			TypeValue[] typeValues = iter.next();

			RdfBean bean = findBySubject(typeValues[0].getValue());
			if (bean != null) {
				rdfBeans.add(bean);
			}
		}

		return rdfBeans;
	}

	public RdfBean findBySubject(String subject) throws SearcherException {

		if (subject == null || subject.isEmpty()) {
			throw new IllegalArgumentException(
					"Subject cannot be null or empty.");
		}

		GraphElementFactory elementFactory = rdfGraph.getElementFactory();
		Resource r = null;
		
		try {
			r = elementFactory.createResource(new URI(subject));
		} catch (GraphElementFactoryException e) {
			LOG.error(e);
			throw new SearcherException(e);
		} catch (URISyntaxException e) {
			LOG.error(e);
			throw new SearcherException(e);
		}
		
		ClosableIterator<Triple> iter = rdfGraph.find(r,
				AnyPredicateNode.ANY_PREDICATE_NODE,
				AnyObjectNode.ANY_OBJECT_NODE).iterator();
				
		List<RdfBean> beans = collapse(iter);
		
		if (beans.isEmpty()) {
			return null;
		}
		
		/*
		 * We trust the collapse method to collapse all possible
		 * results by subject. Therefore, there should only ever
		 * be at most one result returned in the list. Lets just 
		 * return that result instead of a list.
		 */
		return beans.get(0);
	}

	/**
	 * Add a RDF source from a given URL. This eagerly adds the RDF content at
	 * the given URL.
	 * 
	 * @param url
	 *            URL of the RDF resource.
	 * @throws IOException
	 * @throws SearcherException
	 */
	public void addSource(URL url) throws IOException, SearcherException {

		if (url == null) {
			throw new IllegalArgumentException("URL cannot be null");
		}

		InputStream in = getInputStream(url);
		addSource(in, EscapeURL.toEscapedString(url));
	}

	/**
	 * Add a RDF source from a given InputStream. This eagerly adds the RDF
	 * content.
	 * 
	 * @param in
	 * @param url
	 *            The URL of the resource; used to resolve internal relative
	 *            links
	 * @throws IOException
	 * @throws SearcherException
	 */
	public void addSource(InputStream in, String url) throws IOException,
			SearcherException {

		if (in == null) {
			throw new IllegalArgumentException("InputStream cannot be null");
		}

		try {
			parser.parse(in, url);
		} catch (ParseException e) {
			LOG.error(e);
			throw new SearcherException("Unable to parse input", e);
		} catch (StatementHandlerException e) {
			LOG.error(e);
			throw new SearcherException("Unable to add resource", e);
		}
	}

	protected List<RdfBean> collapse(ClosableIterator<Triple> iter) {

		Map<String, RdfBean> beans = new HashMap<String, RdfBean>();

		while (iter.hasNext()) {
			Triple triple = iter.next();
			RdfBean parent = beans.get(triple.getSubject().toString());
			if (parent == null) {
				parent = new RdfBean(triple.getObject().toString(), triple
						.getPredicate().toString(), triple.getSubject()
						.toString());
				beans.put(triple.getSubject().toString(), parent);
			} else {
				RdfBean child = new RdfBean(triple.getObject().toString(),
						triple.getPredicate().toString(), triple.getSubject()
								.toString());
				parent.getTags().add(child);
			}
		}
		
		List<RdfBean> rdfBeans = new LinkedList<RdfBean>();
		Iterator<String> rdfIter = beans.keySet().iterator();
		while (rdfIter.hasNext()) {
			rdfBeans.add(beans.get(rdfIter.next()));
		}
		
		iter.close();
		
		return rdfBeans;
	}

	private static InputStream getInputStream(URL url) throws IOException {
		URLConnection urlConnection = url.openConnection();
		urlConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");
		urlConnection.connect();
		String encoding = urlConnection.getContentEncoding();
		InputStream in;
		if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
			in = new GZIPInputStream(urlConnection.getInputStream());
		} else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
			in = new InflaterInputStream(urlConnection.getInputStream(),
					new Inflater(true));
		} else {
			in = urlConnection.getInputStream();
		}
		return in;
	}
}
