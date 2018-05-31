package com.github.datalking.jdbc.datasource;

import com.github.datalking.jdbc.support.SmartTransactionObject;
import com.github.datalking.jdbc.transaction.SavepointManager;
import com.github.datalking.jdbc.transaction.exception.CannotCreateTransactionException;
import com.github.datalking.jdbc.transaction.exception.NestedTransactionNotSupportedException;
import com.github.datalking.jdbc.transaction.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Savepoint;

/**
 * @author yaoo on 5/30/18
 */
public abstract class JdbcTransactionObjectSupport implements SavepointManager, SmartTransactionObject {

    private static final Logger logger = LoggerFactory.getLogger(JdbcTransactionObjectSupport.class);

    private ConnectionHolder connectionHolder;

    private Integer previousIsolationLevel;

    private boolean savepointAllowed = false;

    public void setConnectionHolder(ConnectionHolder connectionHolder) {
        this.connectionHolder = connectionHolder;
    }

    public ConnectionHolder getConnectionHolder() {
        return this.connectionHolder;
    }

    public boolean hasConnectionHolder() {
        return (this.connectionHolder != null);
    }

    public void setPreviousIsolationLevel(Integer previousIsolationLevel) {
        this.previousIsolationLevel = previousIsolationLevel;
    }

    public Integer getPreviousIsolationLevel() {
        return this.previousIsolationLevel;
    }

    public void setSavepointAllowed(boolean savepointAllowed) {
        this.savepointAllowed = savepointAllowed;
    }

    public boolean isSavepointAllowed() {
        return this.savepointAllowed;
    }

    public void flush() {
        // no-op
    }


    //---------------------------------------------------------------------
    // Implementation of SavepointManager
    //---------------------------------------------------------------------

    /**
     * This implementation creates a JDBC 3.0 Savepoint and returns it.
     *
     * @see java.sql.Connection#setSavepoint
     */
    public Object createSavepoint() throws TransactionException {
        ConnectionHolder conHolder = getConnectionHolderForSavepoint();
        try {
            if (!conHolder.supportsSavepoints()) {
                throw new NestedTransactionNotSupportedException("Cannot create a nested transaction because savepoints are not supported by your JDBC driver");
            }
        } catch (Throwable ex) {
            throw new NestedTransactionNotSupportedException("Cannot create a nested transaction because your JDBC driver is not a JDBC 3.0 driver", ex);
        }
        try {
            return conHolder.createSavepoint();
        } catch (Throwable ex) {
            throw new CannotCreateTransactionException("Could not create JDBC savepoint", ex);
        }
    }

    /**
     * This implementation rolls back to the given JDBC 3.0 Savepoint.
     *
     * @see java.sql.Connection#rollback(java.sql.Savepoint)
     */
    public void rollbackToSavepoint(Object savepoint) throws TransactionException {
        try {
            getConnectionHolderForSavepoint().getConnection().rollback((Savepoint) savepoint);
        } catch (Throwable ex) {
            throw new TransactionException("Could not roll back to JDBC savepoint", ex);
        }
    }

    /**
     * This implementation releases the given JDBC 3.0 Savepoint.
     *
     * @see java.sql.Connection#releaseSavepoint
     */
    public void releaseSavepoint(Object savepoint) throws TransactionException {
        try {
            getConnectionHolderForSavepoint().getConnection().releaseSavepoint((Savepoint) savepoint);
        } catch (Throwable ex) {
            logger.debug("Could not explicitly release JDBC savepoint", ex);
        }
    }

    protected ConnectionHolder getConnectionHolderForSavepoint() throws TransactionException {

        if (!isSavepointAllowed()) {
            throw new NestedTransactionNotSupportedException("Transaction manager does not allow nested transactions");
        }

        if (!hasConnectionHolder()) {
            throw new TransactionException("Cannot create nested transaction if not exposing a JDBC transaction");
        }

        return getConnectionHolder();
    }

}