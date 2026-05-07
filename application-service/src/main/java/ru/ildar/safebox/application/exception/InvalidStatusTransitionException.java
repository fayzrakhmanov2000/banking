package ru.ildar.safebox.application.exception;

import ru.ildar.safebox.application.domain.ApplicationStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Invalid status transition: " + from + " -> " + to);
    }
}
