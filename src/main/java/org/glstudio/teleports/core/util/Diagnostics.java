package org.glstudio.teleports.core.util;

import org.glstudio.nexus.utils.LoggerUtils;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class Diagnostics {
    private static final int MAX_DEPTH = 10;

    private Diagnostics() {
    }

    public static Throwable rootCause(Throwable error) {
        Throwable current = unwrap(error);

        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            Throwable cause = current.getCause();

            if (cause == null || cause == current) {
                return current;
            }
            current = cause;
        }

        return current;
    }

    public static String describeRootCause(Throwable error) {
        Throwable cause = rootCause(error);

        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            return cause.getClass().getSimpleName();
        }

        return detail.replace('\n', ' ').replace('\r', ' ');
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;

        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

    public static void logFailure(String context, Throwable error) {
        Throwable current = unwrap(error);

        StringBuilder chain = new StringBuilder();

        for (int depth = 0; depth < MAX_DEPTH && current != null; depth++) {
            if (depth > 0) {
                chain.append(" <- ");
            }
            chain.append(describe(current));

            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }

        LoggerUtils.logError(context + ": " + chain);
        LoggerUtils.logException(context, rootCause(error));
    }

    private static String describe(Throwable error) {
        String message = error.getMessage() == null ? "(no message)" : error.getMessage();
        String label = error.getClass().getSimpleName() + ": " + message;

        if (error instanceof SQLException sql) {
            return label + " [SQLState=" + sql.getSQLState() + ", errorCode=" + sql.getErrorCode() + "]";
        }

        return label;
    }
}
