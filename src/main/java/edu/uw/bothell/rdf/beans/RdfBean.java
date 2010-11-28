package edu.uw.bothell.rdf.beans;

import java.util.ArrayList;
import java.util.List;

/**
 * RdfBean.
 */
public class RdfBean {
	
	protected String object;
	protected String predicate;
	protected String subject;
	protected List<RdfBean> tags;

	/**
	 * Constructor.
	 * 
	 * @param newObject
	 * @param newPredicate
	 * @param newSubject
	 */
	public RdfBean(String object, String predicate, String subject) {
		super();
		
		this.object = object;
		this.predicate = predicate;
		this.subject = subject;
		tags = new ArrayList<RdfBean>();
	}

	/**
	 * @return the object
	 */
	public String getObject() {
		return object;
	}

	/**
	 * @return the predicate
	 */
	public String getPredicate() {
		return predicate;
	}

	/**
	 * @return the subject
	 */
	public String getSubject() {
		return subject;
	}

	/**
	 * @return the tags
	 */
	public List<RdfBean> getTags() {
		return tags;
	}
}
