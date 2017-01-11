package org.dcm4chee.util;

import java.util.function.Consumer;

public interface QuickChainable<T>
{
    /**
     * For easy "chaining"
     */
    default T edit( Consumer<T> changer ) {
        changer.accept( (T)this );
        return (T)this;
    }
}
