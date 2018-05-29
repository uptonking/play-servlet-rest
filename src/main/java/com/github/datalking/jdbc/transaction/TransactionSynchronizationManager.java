package com.github.datalking.jdbc.transaction;

import com.github.datalking.common.NamedThreadLocal;
import com.github.datalking.common.OrderComparator;
import com.github.datalking.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author yaoo on 5/27/18
 */
public abstract class TransactionSynchronizationManager {

    private static final Logger logger = LoggerFactory.getLogger(TransactionSynchronizationManager.class);

    private static final ThreadLocal<Map<Object, Object>> resources = new NamedThreadLocal<>("Transactional resources");

    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations = new NamedThreadLocal<>("Transaction synchronizations");

    private static final ThreadLocal<String> currentTransactionName = new NamedThreadLocal<>("Current transaction name");

    private static final ThreadLocal<Boolean> currentTransactionReadOnly = new NamedThreadLocal<>("Current transaction read-only status");

    private static final ThreadLocal<Integer> currentTransactionIsolationLevel = new NamedThreadLocal<>("Current transaction isolation level");

    private static final ThreadLocal<Boolean> actualTransactionActive = new NamedThreadLocal<>("Actual transaction active");


    //-------------------------------------------------------------------------
    // Management of transaction-associated resource handles
    //-------------------------------------------------------------------------

    /**
     * Return all resources that are bound to the current thread.
     * <p>Mainly for debugging purposes. Resource managers should always invoke
     * {@code hasResource} for a specific resource key that they are interested in.
     *
     * @return a Map with resource keys (usually the resource factory) and resource
     * values (usually the active resource object), or an empty Map if there are
     * currently no resources bound
     * @see #hasResource
     */
    public static Map<Object, Object> getResourceMap() {
        Map<Object, Object> map = resources.get();
        return (map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap());
    }

    /**
     * Check if there is a resource for the given key bound to the current thread.
     *
     * @param key the key to check (usually the resource factory)
     * @return if there is a value bound to the current thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static boolean hasResource(Object key) {
        Object actualKey =TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(actualKey);
        return (value != null);
    }

    /**
     * Retrieve a resource for the given key that is bound to the current thread.
     *
     * @param key the key to check (usually the resource factory)
     * @return a value bound to the current thread (usually the active
     * resource object), or {@code null} if none
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static Object getResource(Object key) {
        Object actualKey =TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(actualKey);
        if (value != null && logger.isTraceEnabled()) {
            logger.trace("Retrieved value [" + value + "] for key [" + actualKey + "] bound to thread [" +
                    Thread.currentThread().getName() + "]");
        }
        return value;
    }

    /**
     * Actually check the value of the resource that is bound for the given key.
     */
    private static Object doGetResource(Object actualKey) {
        Map<Object, Object> map = resources.get();
        if (map == null) {
            return null;
        }
        Object value = map.get(actualKey);
        // Transparently remove ResourceHolder that was marked as void...
        if (value instanceof ResourceHolder && ((ResourceHolder) value).isVoid()) {
            map.remove(actualKey);
            // Remove entire ThreadLocal if empty...
            if (map.isEmpty()) {
                resources.remove();
            }
            value = null;
        }
        return value;
    }

    /**
     * Bind the given resource for the given key to the current thread.
     *
     * @param key   the key to bind the value to (usually the resource factory)
     * @param value the value to bind (usually the active resource object)
     * @throws IllegalStateException if there is already a value bound to the thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static void bindResource(Object key, Object value) throws IllegalStateException {
        Object actualKey =TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Assert.notNull(value, "Value must not be null");
        Map<Object, Object> map = resources.get();
        // set ThreadLocal Map if none found
        if (map == null) {
            map = new HashMap<>();
            resources.set(map);
        }
        Object oldValue = map.put(actualKey, value);
        // Transparently suppress a ResourceHolder that was marked as void...
        if (oldValue instanceof ResourceHolder && ((ResourceHolder) oldValue).isVoid()) {
            oldValue = null;
        }
        if (oldValue != null) {
            throw new IllegalStateException("Already value [" + oldValue + "] for key [" +
                    actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Bound value [" + value + "] for key [" + actualKey + "] to thread [" +
                    Thread.currentThread().getName() + "]");
        }
    }

    /**
     * Unbind a resource for the given key from the current thread.
     *
     * @param key the key to unbind (usually the resource factory)
     * @return the previously bound value (usually the active resource object)
     * @throws IllegalStateException if there is no value bound to the thread
     * @see ResourceTransactionManager#getResourceFactory()
     */
    public static Object unbindResource(Object key) throws IllegalStateException {
        Object actualKey =TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doUnbindResource(actualKey);
        if (value == null) {
            throw new IllegalStateException(
                    "No value for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }
        return value;
    }

    /**
     * Unbind a resource for the given key from the current thread.
     *
     * @param key the key to unbind (usually the resource factory)
     * @return the previously bound value, or {@code null} if none bound
     */
    public static Object unbindResourceIfPossible(Object key) {
        Object actualKey =TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        return doUnbindResource(actualKey);
    }

    /**
     * Actually remove the value of the resource that is bound for the given key.
     */
    private static Object doUnbindResource(Object actualKey) {
        Map<Object, Object> map = resources.get();
        if (map == null) {
            return null;
        }
        Object value = map.remove(actualKey);
        // Remove entire ThreadLocal if empty...
        if (map.isEmpty()) {
            resources.remove();
        }
        // Transparently suppress a ResourceHolder that was marked as void...
        if (value instanceof ResourceHolder && ((ResourceHolder) value).isVoid()) {
            value = null;
        }
        if (value != null && logger.isTraceEnabled()) {
            logger.trace("Removed value [" + value + "] for key [" + actualKey + "] from thread [" +
                    Thread.currentThread().getName() + "]");
        }
        return value;
    }


    //-------------------------------------------------------------------------
    // Management of transaction synchronizations
    //-------------------------------------------------------------------------

    /**
     * Return if transaction synchronization is active for the current thread.
     * Can be called before register to avoid unnecessary instance creation.
     *
     * @see #registerSynchronization
     */
    public static boolean isSynchronizationActive() {
        return (synchronizations.get() != null);
    }

    /**
     * Activate transaction synchronization for the current thread.
     * Called by a transaction manager on transaction begin.
     *
     * @throws IllegalStateException if synchronization is already active
     */
    public static void initSynchronization() throws IllegalStateException {
        if (isSynchronizationActive()) {
            throw new IllegalStateException("Cannot activate transaction synchronization - already active");
        }
        logger.trace("Initializing transaction synchronization");
        synchronizations.set(new LinkedHashSet<>());
    }

    /**
     * Register a new transaction synchronization for the current thread.
     * Typically called by resource management code.
     * <p>Note that synchronizations can implement the org.springframework.core.Ordered interface.
     * They will be executed in an order according to their order value (if any).
     *
     * @param synchronization the synchronization object to register
     * @throws IllegalStateException if transaction synchronization is not active
     */
    public static void registerSynchronization(TransactionSynchronization synchronization)
            throws IllegalStateException {

        Assert.notNull(synchronization, "TransactionSynchronization must not be null");
        if (!isSynchronizationActive()) {
            throw new IllegalStateException("Transaction synchronization is not active");
        }
        synchronizations.get().add(synchronization);
    }

    /**
     * Return an unmodifiable snapshot list of all registered synchronizations
     * for the current thread.
     *
     * @return unmodifiable List of TransactionSynchronization instances
     * @throws IllegalStateException if synchronization is not active
     * @see TransactionSynchronization
     */
    public static List<TransactionSynchronization> getSynchronizations() throws IllegalStateException {
        Set<TransactionSynchronization> synchs = synchronizations.get();
        if (synchs == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        }
        // Return unmodifiable snapshot, to avoid ConcurrentModificationExceptions
        // while iterating and invoking synchronization callbacks that in turn
        // might register further synchronizations.
        if (synchs.isEmpty()) {
            return Collections.emptyList();
        } else {
            // Sort lazily here, not in registerSynchronization.
            List<TransactionSynchronization> sortedSynchs = new ArrayList<>(synchs);
            OrderComparator.sort(sortedSynchs);
            return Collections.unmodifiableList(sortedSynchs);
        }
    }

    /**
     * Deactivate transaction synchronization for the current thread.
     * Called by the transaction manager on transaction cleanup.
     *
     * @throws IllegalStateException if synchronization is not active
     */
    public static void clearSynchronization() throws IllegalStateException {
        if (!isSynchronizationActive()) {
            throw new IllegalStateException("Cannot deactivate transaction synchronization - not active");
        }
        logger.trace("Clearing transaction synchronization");
        synchronizations.remove();
    }


    //-------------------------------------------------------------------------
    // Exposure of transaction characteristics
    //-------------------------------------------------------------------------

    /**
     * Expose the name of the current transaction, if any.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param name the name of the transaction, or {@code null} to reset it
     */
    public static void setCurrentTransactionName(String name) {
        currentTransactionName.set(name);
    }

    /**
     * Return the name of the current transaction, or {@code null} if none set.
     * To be called by resource management code for optimizations per use case,
     * for example to optimize fetch strategies for specific named transactions.
     */
    public static String getCurrentTransactionName() {
        return currentTransactionName.get();
    }

    /**
     * Expose a read-only flag for the current transaction.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param readOnly {@code true} to mark the current transaction
     *                 as read-only; {@code false} to reset such a read-only marker
     */
    public static void setCurrentTransactionReadOnly(boolean readOnly) {
        currentTransactionReadOnly.set(readOnly ? Boolean.TRUE : null);
    }

    /**
     * Return whether the current transaction is marked as read-only.
     * To be called by resource management code when preparing a newly
     * created resource (for example, a Hibernate Session).
     * <p>Note that transaction synchronizations receive the read-only flag
     * as argument for the {@code beforeCommit} callback, to be able
     * to suppress change detection on commit. The present method is meant
     * to be used for earlier read-only checks, for example to set the
     * flush mode of a Hibernate Session to "FlushMode.NEVER" upfront.
     */
    public static boolean isCurrentTransactionReadOnly() {
        return (currentTransactionReadOnly.get() != null);
    }

    /**
     * Expose an isolation level for the current transaction.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param isolationLevel the isolation level to expose, according to the
     *                       JDBC Connection constants (equivalent to the corresponding Spring
     *                       TransactionDefinition constants), or {@code null} to reset it
     */
    public static void setCurrentTransactionIsolationLevel(Integer isolationLevel) {
        currentTransactionIsolationLevel.set(isolationLevel);
    }

    /**
     * Return the isolation level for the current transaction, if any.
     * To be called by resource management code when preparing a newly
     * created resource (for example, a JDBC Connection).
     *
     * @return the currently exposed isolation level, according to the
     * JDBC Connection constants (equivalent to the corresponding Spring
     * TransactionDefinition constants), or {@code null} if none
     */
    public static Integer getCurrentTransactionIsolationLevel() {
        return currentTransactionIsolationLevel.get();
    }

    /**
     * Expose whether there currently is an actual transaction active.
     * Called by the transaction manager on transaction begin and on cleanup.
     *
     * @param active {@code true} to mark the current thread as being associated
     *               with an actual transaction; {@code false} to reset that marker
     */
    public static void setActualTransactionActive(boolean active) {
        actualTransactionActive.set(active ? Boolean.TRUE : null);
    }

    /**
     * Return whether there currently is an actual transaction active.
     * This indicates whether the current thread is associated with an actual
     * transaction rather than just with active transaction synchronization.
     * <p>To be called by resource management code that wants to discriminate
     * between active transaction synchronization (with or without backing
     * resource transaction; also on PROPAGATION_SUPPORTS) and an actual
     * transaction being active (with backing resource transaction;
     * on PROPAGATION_REQUIRES, PROPAGATION_REQUIRES_NEW, etc).
     *
     * @see #isSynchronizationActive()
     */
    public static boolean isActualTransactionActive() {
        return (actualTransactionActive.get() != null);
    }


    /**
     * Clear the entire transaction synchronization state for the current thread:
     * registered synchronizations as well as the various transaction characteristics.
     *
     * @see #setActualTransactionActive
     */
    public static void clear() {
        clearSynchronization();
        setCurrentTransactionName(null);
        setCurrentTransactionReadOnly(false);
        setCurrentTransactionIsolationLevel(null);
        setActualTransactionActive(false);
    }

}