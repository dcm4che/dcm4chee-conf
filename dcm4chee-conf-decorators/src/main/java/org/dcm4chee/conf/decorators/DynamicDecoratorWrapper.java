package org.dcm4chee.conf.decorators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import java.util.Collection;

public class DynamicDecoratorWrapper<T> {
	
	private static final Logger LOG = LoggerFactory.getLogger(DynamicDecoratorWrapper.class);

	@Inject
	@ConfiguredDynamicDecorators
	Instance<Collection<DelegatingServiceImpl<T>>> dynamicDecorators;
	
	private T wrappedDecorator = null;
	
    public final T wrapWithDynamicDecorators(T delegate) {
    	if (wrappedDecorator == null) {
    		getWrappedDynamicDecorators(delegate);
    	}
    	LOG.trace("Returning {}.", wrappedDecorator);
    	
    	return wrappedDecorator;
    }
    
    private synchronized void getWrappedDynamicDecorators(T delegate) { 
    	if (wrappedDecorator != null) {
    		return;
    	}
    	DelegatingServiceImpl<T> theService = new DelegatingServiceImpl<T>();
	    theService.setOrig(delegate);
	    
	    for (Collection<DelegatingServiceImpl<T>> collectionDynamicDecoratorsForType : dynamicDecorators) {
	    	for (DelegatingServiceImpl<T> dynamicDecorator : collectionDynamicDecoratorsForType) {
	    		LOG.trace("Iterating over {}", dynamicDecorator.getClass());
	    		dynamicDecorator.setDelegate(theService);
	    		dynamicDecorator.setOrig(dynamicDecorator.getTypeObject());
	    		theService = dynamicDecorator;
	    	}
	    }
	    
	    wrappedDecorator = theService.getOrig();
	}
}
