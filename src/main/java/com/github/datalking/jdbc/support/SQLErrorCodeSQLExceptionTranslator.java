package com.github.datalking.jdbc.support;

import com.github.datalking.jdbc.BadSqlGrammarException;
import com.github.datalking.jdbc.InvalidResultSetAccessException;
import com.github.datalking.jdbc.UncategorizedSQLException;
import com.github.datalking.jdbc.dao.DataAccessException;
import com.github.datalking.jdbc.dao.DuplicateKeyException;
import com.github.datalking.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * @author yaoo on 5/27/18
 */
public class SQLErrorCodeSQLExceptionTranslator implements SQLExceptionTranslator {

    private static final Logger logger = LoggerFactory.getLogger(SQLErrorCodeSQLExceptionTranslator.class);

    private static final int MESSAGE_ONLY_CONSTRUCTOR = 1;
    private static final int MESSAGE_THROWABLE_CONSTRUCTOR = 2;
    private static final int MESSAGE_SQLEX_CONSTRUCTOR = 3;
    private static final int MESSAGE_SQL_THROWABLE_CONSTRUCTOR = 4;
    private static final int MESSAGE_SQL_SQLEX_CONSTRUCTOR = 5;

    private SQLErrorCodes sqlErrorCodes;

    /**
     * Constructor for use as a JavaBean.
     * The SqlErrorCodes or DataSource property must be set.
     */
    public SQLErrorCodeSQLExceptionTranslator() {
//        if (JdkVersion.getMajorJavaVersion() >= JdkVersion.JAVA_16) {
//            setFallbackTranslator(new SQLExceptionSubclassTranslator());
//        }
//        else {
//            setFallbackTranslator(new SQLStateSQLExceptionTranslator());
//        }
    }

    /**
     * Create a SQL error code translator for the given DataSource.
     * Invoking this constructor will cause a Connection to be obtained
     * from the DataSource to get the metadata.
     *
     * @param dataSource DataSource to use to find metadata and establish
     *                   which error codes are usable
     */
    public SQLErrorCodeSQLExceptionTranslator(DataSource dataSource) {
        this();
        setDataSource(dataSource);
    }

    /**
     * Create a SQL error code translator for the given database product name.
     * Invoking this constructor will avoid obtaining a Connection from the
     * DataSource to get the metadata.
     *
     * @param dbName the database product name that identifies the error codes entry
     * @see java.sql.DatabaseMetaData#getDatabaseProductName()
     */
    public SQLErrorCodeSQLExceptionTranslator(String dbName) {
        this();
        setDatabaseProductName(dbName);
    }

    /**
     * Create a SQLErrorCode translator given these error codes.
     * Does not require a database metadata lookup to be performed using a connection.
     *
     * @param sec error codes
     */
    public SQLErrorCodeSQLExceptionTranslator(SQLErrorCodes sec) {
        this();
        this.sqlErrorCodes = sec;
    }

    @Override
    public DataAccessException translate(String task, String sql, SQLException ex) {
        Assert.notNull(ex, "Cannot translate a null SQLException");
        if (task == null) {
            task = "";
        }
        if (sql == null) {
            sql = "";
        }

        DataAccessException dex = doTranslate(task, sql, ex);
        if (dex != null) {
            // Specific exception match found.
            return dex;
        }
        // Looking for a fallback...
//        SQLExceptionTranslator fallback = getFallbackTranslator();
//        if (fallback != null) {
//            return fallback.translate(task, sql, ex);
//        }
        // We couldn't identify it more precisely.
        return new UncategorizedSQLException(task, sql, ex);
    }

    /**
     * Set the DataSource for this translator.
     * <p>Setting this property will cause a Connection to be obtained from
     * the DataSource to get the metadata.
     *
     * @param dataSource DataSource to use to find metadata and establish
     *                   which error codes are usable
     * @see java.sql.DatabaseMetaData#getDatabaseProductName()
     */
    public void setDataSource(DataSource dataSource) {
        this.sqlErrorCodes = SQLErrorCodesFactory.getInstance().getErrorCodes(dataSource);
    }

    /**
     * Set the database product name for this translator.
     * <p>Setting this property will avoid obtaining a Connection from the DataSource
     * to get the metadata.
     *
     * @param dbName the database product name that identifies the error codes entry
     * @see SQLErrorCodesFactory#getErrorCodes(String)
     * @see java.sql.DatabaseMetaData#getDatabaseProductName()
     */
    public void setDatabaseProductName(String dbName) {
        this.sqlErrorCodes = SQLErrorCodesFactory.getInstance().getErrorCodes(dbName);
    }

    /**
     * Set custom error codes to be used for translation.
     *
     * @param sec custom error codes to use
     */
    public void setSqlErrorCodes(SQLErrorCodes sec) {
        this.sqlErrorCodes = sec;
    }

    /**
     * Return the error codes used by this translator.
     * Usually determined via a DataSource.
     *
     * @see #setDataSource
     */
    public SQLErrorCodes getSqlErrorCodes() {
        return this.sqlErrorCodes;
    }


    //    @Override
    protected DataAccessException doTranslate(String task, String sql, SQLException ex) {
        SQLException sqlEx = ex;
        if (sqlEx instanceof BatchUpdateException && sqlEx.getNextException() != null) {
            SQLException nestedSqlEx = sqlEx.getNextException();
            if (nestedSqlEx.getErrorCode() > 0 || nestedSqlEx.getSQLState() != null) {
                logger.debug("Using nested SQLException from the BatchUpdateException");
                sqlEx = nestedSqlEx;
            }
        }

        // First, try custom translation from overridden method.
        DataAccessException dex = customTranslate(task, sql, sqlEx);
        if (dex != null) {
            return dex;
        }

        // Next, try the custom SQLException translator, if available.
        if (this.sqlErrorCodes != null) {
            SQLExceptionTranslator customTranslator = this.sqlErrorCodes.getCustomSqlExceptionTranslator();
            if (customTranslator != null) {
                DataAccessException customDex = customTranslator.translate(task, sql, sqlEx);
                if (customDex != null) {
                    return customDex;
                }
            }
        }

        // Check SQLErrorCodes with corresponding error code, if available.
        if (this.sqlErrorCodes != null) {
            String errorCode;
            if (this.sqlErrorCodes.isUseSqlStateForTranslation()) {
                errorCode = sqlEx.getSQLState();
            } else {
                // Try to find SQLException with actual error code, looping through the causes.
                // E.g. applicable to java.sql.DataTruncation as of JDK 1.6.
                SQLException current = sqlEx;
                while (current.getErrorCode() == 0 && current.getCause() instanceof SQLException) {
                    current = (SQLException) current.getCause();
                }
                errorCode = Integer.toString(current.getErrorCode());
            }

            if (errorCode != null) {
                // Look for defined custom translations first.
//                CustomSQLErrorCodesTranslation[] customTranslations = this.sqlErrorCodes.getCustomTranslations();
//                if (customTranslations != null) {
//                    for (CustomSQLErrorCodesTranslation customTranslation : customTranslations) {
//                        if (Arrays.binarySearch(customTranslation.getErrorCodes(), errorCode) >= 0) {
//                            if (customTranslation.getExceptionClass() != null) {
//                                DataAccessException customException = createCustomException(
//                                        task, sql, sqlEx, customTranslation.getExceptionClass());
//                                if (customException != null) {
//                                    logTranslation(task, sql, sqlEx, true);
//                                    return customException;
//                                }
//                            }
//                        }
//                    }
//                }
                // Next, look for grouped error codes.
                if (Arrays.binarySearch(this.sqlErrorCodes.getBadSqlGrammarCodes(), errorCode) >= 0) {
                    logTranslation(task, sql, sqlEx, false);
                    return new BadSqlGrammarException(task, sql, sqlEx);
                } else if (Arrays.binarySearch(this.sqlErrorCodes.getInvalidResultSetAccessCodes(), errorCode) >= 0) {
                    logTranslation(task, sql, sqlEx, false);
                    return new InvalidResultSetAccessException(task, sql, sqlEx);
                } else if (Arrays.binarySearch(this.sqlErrorCodes.getDuplicateKeyCodes(), errorCode) >= 0) {
                    logTranslation(task, sql, sqlEx, false);
                    return new DuplicateKeyException(buildMessage(task, sql, sqlEx), sqlEx);
                }

//                else if (Arrays.binarySearch(this.sqlErrorCodes.getDataIntegrityViolationCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new DataIntegrityViolationException(buildMessage(task, sql, sqlEx), sqlEx);
//                } else if (Arrays.binarySearch(this.sqlErrorCodes.getPermissionDeniedCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new PermissionDeniedDataAccessException(buildMessage(task, sql, sqlEx), sqlEx);
//                } else if (Arrays.binarySearch(this.sqlErrorCodes.getDataAccessResourceFailureCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new DataAccessResourceFailureException(buildMessage(task, sql, sqlEx), sqlEx);
//                } else if (Arrays.binarySearch(this.sqlErrorCodes.getTransientDataAccessResourceCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new TransientDataAccessResourceException(buildMessage(task, sql, sqlEx), sqlEx);
//                } else if (Arrays.binarySearch(this.sqlErrorCodes.getCannotAcquireLockCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new CannotAcquireLockException(buildMessage(task, sql, sqlEx), sqlEx);
//                } else if (Arrays.binarySearch(this.sqlErrorCodes.getDeadlockLoserCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new DeadlockLoserDataAccessException(buildMessage(task, sql, sqlEx), sqlEx);
//                } else if (Arrays.binarySearch(this.sqlErrorCodes.getCannotSerializeTransactionCodes(), errorCode) >= 0) {
//                    logTranslation(task, sql, sqlEx, false);
//                    return new CannotSerializeTransactionException(buildMessage(task, sql, sqlEx), sqlEx);
//                }
            }
        }

        // We couldn't identify it more precisely - let's hand it over to the SQLState fallback translator.
        if (logger.isDebugEnabled()) {
            String codes;
            if (this.sqlErrorCodes != null && this.sqlErrorCodes.isUseSqlStateForTranslation()) {
                codes = "SQL state '" + sqlEx.getSQLState() + "', error code '" + sqlEx.getErrorCode();
            } else {
                codes = "Error code '" + sqlEx.getErrorCode() + "'";
            }
            logger.debug("Unable to translate SQLException with " + codes + ", will now try the fallback translator");
        }

        return null;
    }

    protected String buildMessage(String task, String sql, SQLException ex) {
        return task + "; SQL [" + sql + "]; " + ex.getMessage();
    }

    /**
     * Subclasses can override this method to attempt a custom mapping from SQLException
     * to DataAccessException.
     *
     * @param task  readable text describing the task being attempted
     * @param sql   SQL query or update that caused the problem. May be {@code null}.
     * @param sqlEx the offending SQLException
     * @return null if no custom translation was possible, otherwise a DataAccessException
     * resulting from custom translation. This exception should include the sqlEx parameter
     * as a nested root cause. This implementation always returns null, meaning that
     * the translator always falls back to the default error codes.
     */
    protected DataAccessException customTranslate(String task, String sql, SQLException sqlEx) {
        return null;
    }

    /**
     * Create a custom DataAccessException, based on a given exception
     * class from a CustomSQLErrorCodesTranslation definition.
     *
     * @param task           readable text describing the task being attempted
     * @param sql            SQL query or update that caused the problem. May be {@code null}.
     * @param sqlEx          the offending SQLException
     * @param exceptionClass the exception class to use, as defined in the
     *                       CustomSQLErrorCodesTranslation definition
     * @return null if the custom exception could not be created, otherwise
     * the resulting DataAccessException. This exception should include the
     * sqlEx parameter as a nested root cause.
     */
    protected DataAccessException createCustomException(
            String task, String sql, SQLException sqlEx, Class exceptionClass) {

        // find appropriate constructor
        try {
            int constructorType = 0;
            Constructor[] constructors = exceptionClass.getConstructors();
            for (Constructor constructor : constructors) {
                Class[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0].equals(String.class)) {
                    if (constructorType < MESSAGE_ONLY_CONSTRUCTOR)
                        constructorType = MESSAGE_ONLY_CONSTRUCTOR;
                }
                if (parameterTypes.length == 2 && parameterTypes[0].equals(String.class) &&
                        parameterTypes[1].equals(Throwable.class)) {
                    if (constructorType < MESSAGE_THROWABLE_CONSTRUCTOR)
                        constructorType = MESSAGE_THROWABLE_CONSTRUCTOR;
                }
                if (parameterTypes.length == 2 && parameterTypes[0].equals(String.class) &&
                        parameterTypes[1].equals(SQLException.class)) {
                    if (constructorType < MESSAGE_SQLEX_CONSTRUCTOR)
                        constructorType = MESSAGE_SQLEX_CONSTRUCTOR;
                }
                if (parameterTypes.length == 3 && parameterTypes[0].equals(String.class) &&
                        parameterTypes[1].equals(String.class) && parameterTypes[2].equals(Throwable.class)) {
                    if (constructorType < MESSAGE_SQL_THROWABLE_CONSTRUCTOR)
                        constructorType = MESSAGE_SQL_THROWABLE_CONSTRUCTOR;
                }
                if (parameterTypes.length == 3 && parameterTypes[0].equals(String.class) &&
                        parameterTypes[1].equals(String.class) && parameterTypes[2].equals(SQLException.class)) {
                    if (constructorType < MESSAGE_SQL_SQLEX_CONSTRUCTOR)
                        constructorType = MESSAGE_SQL_SQLEX_CONSTRUCTOR;
                }
            }

            // invoke constructor
            Constructor exceptionConstructor;
            switch (constructorType) {
                case MESSAGE_SQL_SQLEX_CONSTRUCTOR:
                    Class[] messageAndSqlAndSqlExArgsClass = new Class[]{String.class, String.class, SQLException.class};
                    Object[] messageAndSqlAndSqlExArgs = new Object[]{task, sql, sqlEx};
                    exceptionConstructor = exceptionClass.getConstructor(messageAndSqlAndSqlExArgsClass);
                    return (DataAccessException) exceptionConstructor.newInstance(messageAndSqlAndSqlExArgs);
                case MESSAGE_SQL_THROWABLE_CONSTRUCTOR:
                    Class[] messageAndSqlAndThrowableArgsClass = new Class[]{String.class, String.class, Throwable.class};
                    Object[] messageAndSqlAndThrowableArgs = new Object[]{task, sql, sqlEx};
                    exceptionConstructor = exceptionClass.getConstructor(messageAndSqlAndThrowableArgsClass);
                    return (DataAccessException) exceptionConstructor.newInstance(messageAndSqlAndThrowableArgs);
                case MESSAGE_SQLEX_CONSTRUCTOR:
                    Class[] messageAndSqlExArgsClass = new Class[]{String.class, SQLException.class};
                    Object[] messageAndSqlExArgs = new Object[]{task + ": " + sqlEx.getMessage(), sqlEx};
                    exceptionConstructor = exceptionClass.getConstructor(messageAndSqlExArgsClass);
                    return (DataAccessException) exceptionConstructor.newInstance(messageAndSqlExArgs);
                case MESSAGE_THROWABLE_CONSTRUCTOR:
                    Class[] messageAndThrowableArgsClass = new Class[]{String.class, Throwable.class};
                    Object[] messageAndThrowableArgs = new Object[]{task + ": " + sqlEx.getMessage(), sqlEx};
                    exceptionConstructor = exceptionClass.getConstructor(messageAndThrowableArgsClass);
                    return (DataAccessException) exceptionConstructor.newInstance(messageAndThrowableArgs);
                case MESSAGE_ONLY_CONSTRUCTOR:
                    Class[] messageOnlyArgsClass = new Class[]{String.class};
                    Object[] messageOnlyArgs = new Object[]{task + ": " + sqlEx.getMessage()};
                    exceptionConstructor = exceptionClass.getConstructor(messageOnlyArgsClass);
                    return (DataAccessException) exceptionConstructor.newInstance(messageOnlyArgs);
                default:
                    if (logger.isWarnEnabled()) {
                        logger.warn("Unable to find appropriate constructor of custom exception class [" +
                                exceptionClass.getName() + "]");
                    }
                    return null;
            }
        } catch (Throwable ex) {
            if (logger.isWarnEnabled()) {
                logger.warn("Unable to instantiate custom exception class [" + exceptionClass.getName() + "]", ex);
            }
            return null;
        }
    }

    private void logTranslation(String task, String sql, SQLException sqlEx, boolean custom) {
        if (logger.isDebugEnabled()) {
            String intro = custom ? "Custom translation of" : "Translating";
            logger.debug(intro + " SQLException with SQL state '" + sqlEx.getSQLState() +
                    "', error code '" + sqlEx.getErrorCode() + "', message [" + sqlEx.getMessage() +
                    "]; SQL was [" + sql + "] for task [" + task + "]");
        }
    }

}
