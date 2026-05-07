package ru.ildar.safebox.application.exception;

public class DuplicateEventException extends RuntimeException {
    public DuplicateEventException(String eventId) {
        super("Duplicate event: " + eventId);
    }
}
