package com.upi.mesh.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.*;
import java.util.Base64;

@Component
public class ServerKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);

    private KeyPair keyPair;

    @PostConstruct
    public void generateKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();
        String fp = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        log.info("Server RSA-2048 keypair generated. Public key (first 40 chars): {}...",
                fp.substring(0, 40));
    }

    public PublicKey getPublicKey()   { return keyPair.getPublic(); }
    public PrivateKey getPrivateKey() { return keyPair.getPrivate(); }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}
