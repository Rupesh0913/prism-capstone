package com.prism.gateway.exception;

public class AuthenticationException extends RuntimeException{

    public AuthenticationException(String message)  {
        super(message);
    }
}
