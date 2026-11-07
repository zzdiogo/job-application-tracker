package dev.zzdiogo.jobtracker.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared error factories. */
public final class Errors {

    private Errors() {}

    /**
     * Used both for missing records and for records owned by someone else, so a user can't
     * discover which IDs exist.
     */
    public static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }
}
