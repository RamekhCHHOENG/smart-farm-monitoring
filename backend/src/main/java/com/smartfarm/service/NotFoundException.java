package com.smartfarm.service;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Long id) {
        super(what + " " + id + " not found");
    }

    /** A complete message, for cases where an id would mean nothing to the person (an expired invite link). */
    public NotFoundException(String message) {
        super(message);
    }
}
