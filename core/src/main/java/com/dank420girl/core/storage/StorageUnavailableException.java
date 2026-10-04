package com.dank420girl.core.storage;

/**
 * The storage cannot be reached at the moment, for example because the database server is down or the
 * connection to it was lost. Other errors (such as a failing query) are not reported this way.
 */
public class StorageUnavailableException extends Exception {
    private static final long serialVersionUID = 1L;

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
