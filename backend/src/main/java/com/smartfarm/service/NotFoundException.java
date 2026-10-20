package com.smartfarm.service;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Long id) {
        super(what + " " + id + " not found");
    }
}
