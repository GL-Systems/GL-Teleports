package org.glstudio.teleports.modules.migration;

/**
 * A migration that cannot go ahead at all, as opposed to a single bad entry.
 *
 * The message key points at language.yml so the administrator gets a translated explanation; the exception
 * message is the English detail written to the console.
 */
public class MigrationException extends Exception {
    private final String messageKey;
    private final String[] placeholders;

    public MigrationException(String messageKey, String detail, String... placeholders) {
        super(detail);
        this.messageKey = messageKey;
        this.placeholders = placeholders;
    }

    public MigrationException(String messageKey, String detail, Throwable cause, String... placeholders) {
        super(detail, cause);
        this.messageKey = messageKey;
        this.placeholders = placeholders;
    }

    public String messageKey() {
        return messageKey;
    }

    public String[] placeholders() {
        return placeholders.clone();
    }
}
