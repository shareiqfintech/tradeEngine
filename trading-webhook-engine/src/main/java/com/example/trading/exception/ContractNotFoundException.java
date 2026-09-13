package com.example.trading.exception;

/** Thrown when {@code OptionContractResolver} cannot resolve a valid, tradeable contract. */
public class ContractNotFoundException extends RuntimeException {
    public ContractNotFoundException(String message) {
        super(message);
    }
}
