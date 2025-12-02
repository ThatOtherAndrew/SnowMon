package utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class NonceManager {
    private final Set<String> usedNonces = Collections.synchronizedSet(new HashSet<>());

    public boolean validateNonce(String nonce) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }
        // add() returns true if the element was added (i.e. was not already present)
        return usedNonces.add(nonce);
    }
}
