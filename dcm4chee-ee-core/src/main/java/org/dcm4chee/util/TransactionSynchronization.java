package org.dcm4chee.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.transaction.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@ApplicationScoped
public class TransactionSynchronization {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionSynchronization.class);
    
    private static final String ON_COMMIT_RUNNER_TX_RESOURCE = OnCommitRunner.class.getName();

    @Resource(lookup = "java:jboss/TransactionManager")
    private TransactionManager tmManager;

    @Resource(lookup = "java:comp/TransactionSynchronizationRegistry")
    private TransactionSynchronizationRegistry synchronizationRegistry;

    public TransactionSynchronizationRegistry getSynchronizationRegistry() {
        return synchronizationRegistry;
    }

    public TransactionManager getTransactionManager() {
        return tmManager;
    }

    /**
     * @return {@link Status} of ongoing transaction (works also if no transaction is bound to the thread currently)
     */
    public int getStatus() {
        try {
            return tmManager.getStatus();
        } catch (SystemException e) {
            throw new RuntimeException("Error while inquiring transaction status", e);
        }
    }

    /**
     * Executes a given runnable once (and only if) the current transaction was successfully committed.
     * For multiple calls, the order of execution is preserved.
     * </p>
     * <ul>
     * <li>If called within a transaction then the runnable is called after the ongoing transaction has been 
     * successfully committed (=AFTER_COMPLETION phase). The runnable is executed in the thread that
     * committed the transaction</li>
     * <li>If called without a transaction then the runnable is simply execute right-away using the calling thread
     *</ul>
     * @param r what to execute
     */
    public void afterSuccessfulCommit(Runnable r) {
        afterSuccessfulCommit(r, CallingThreadExecutor.INSTANCE);
    }
    
    /*
     * Singleton executor that simply uses the calling thread for task execution
     */
    private enum CallingThreadExecutor implements Executor {
        INSTANCE;
        
        @Override
        public void execute(Runnable r) {
            r.run();
        }
        
    }
    
    /**
     * Executes a given runnable once (and only if) the current transaction was successfully committed.
     * For multiple calls, the order of execution is preserved.
     * </p>
     * <ul>
     * <li>If called within a transaction then the runnable is called after the ongoing transaction has been 
     * successfully committed (=AFTER_COMPLETION phase)</li>
     * <li>If called without a transaction then the runnable is simply execute right-away using the calling thread
     *</ul>
     *In all cases the runnable is executed in the give executor
     * @param r what to execute
     * @param executor Executor used to execute the runnable
     */
    public void afterSuccessfulCommit(Runnable r, Executor executor) {

        try {
            Transaction transaction = tmManager.getTransaction();

            if (transaction == null) {
                // if no tx - just execute the callback
                executor.execute(r);
            } else {
                // otherwise register the callback if necessary and add to the list
                OnCommitRunner onCommitRunner = (OnCommitRunner)synchronizationRegistry.getResource(ON_COMMIT_RUNNER_TX_RESOURCE);
                if (onCommitRunner == null) {
                    onCommitRunner = new OnCommitRunner(executor);
                    transaction.registerSynchronization(onCommitRunner);
                    synchronizationRegistry.putResource(ON_COMMIT_RUNNER_TX_RESOURCE, onCommitRunner);
                }
                
                onCommitRunner.addRunAfterCommit(r);
            }
        } catch (SystemException | RollbackException e) {
            throw new RuntimeException("Cannot register on-commit hook", e);
        }
    }
    
    private static class OnCommitRunner implements Synchronization {
        private final Executor executor;
        private final List<Runnable> toRunAfterCommit = new ArrayList<>();
        
        private OnCommitRunner(Executor executor) {
            this.executor = executor;
        }
        
        private void addRunAfterCommit(Runnable runnable) {
            toRunAfterCommit.add(runnable);
        }
        
        @Override
        public void beforeCompletion() {
            // NOP
        }

        @Override
        public void afterCompletion(int status) {
            // run only if successfully committed
            try {
                if (status == Status.STATUS_COMMITTED) {
                    for (Runnable runnable : toRunAfterCommit) {
                        try {
                            executor.execute(runnable);
                        } catch (Exception e) {
                            LOG.error("Error while executing a callback after transaction commit",
                                    e);
                        }
                    }
                }
            } finally {
                toRunAfterCommit.clear();
            }
        }
    }
}
