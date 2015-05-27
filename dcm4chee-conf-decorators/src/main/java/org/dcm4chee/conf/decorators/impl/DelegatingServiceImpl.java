package org.dcm4chee.conf.decorators.impl;

/* * 
 * 1) getTypeObject() method works because the DelegatingXXService classes we have all extend this class with an explicit type T
 * 2) I would have preferred to have the set/get methods be final, but WELD needs to extend the class for its proxies so that can't be done
 * 3) CDI needs an empty constructor - initially tried to have a constructor that passed in the orig value, but this wouldn't allow the class to be injected
 *		-> could get around the empty constructor requirement by using a producer  
 */

public class DelegatingServiceImpl<T> {
	private DelegatingServiceImpl<T> delegate;
	private T orig;
	
	@SuppressWarnings("unchecked")
	public T getTypeObject() {
		return (T) this;
	}

	public void setOrig(T orig) {
		this.orig = orig;
	}
	
	
	public T getOrig() {
		return orig;
	}

	public DelegatingServiceImpl<T> getDelegate() {
		return delegate;
	}
	
	public void setDelegate(DelegatingServiceImpl<T> theService) {
		this.delegate = theService; 
	}
	
	public T getNextDecorator() {
		return getDelegate().getOrig();
	}
}
