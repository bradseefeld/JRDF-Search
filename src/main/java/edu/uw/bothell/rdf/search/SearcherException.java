package edu.uw.bothell.rdf.search;

public class SearcherException extends Exception {

	public SearcherException(String msg, Throwable t) {
		super(msg, t);
	}
	
	public SearcherException(Throwable t) {
		super(t);
	}
}
