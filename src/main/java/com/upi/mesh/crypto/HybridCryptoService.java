package com.upi.mesh.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.mesh.model.PaymentInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * Hybrid encryption — RSA-OAEP-SHA256 encrypts a fresh AES-256 key,
 * AES-GCM encrypts the payload. Any tampering fails decryption.
 *
 * Wire format (base64):
 *   [ 256 bytes RSA(AES key) ][ 12 bytes IV ][ AES-GCM ciphertext + 16-byte tag ]
 */
@Service
public class HybridCryptoService {

    private static final String RSA_ALG = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_ALG = "AES/GCM/NoPadding";
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int RSA_KEY_BYTES = 256;

    private final SecureRandom rng = new SecureRandom();
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    private ServerKeyHolder serverKey;

    public String encrypt(PaymentInstruction instruction, PublicKey publicKey) throws Exception {
        byte[] plaintext = json.writeValueAsBytes(instruction);

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS);
        SecretKey aesKey = kg.generateKey();

        byte[] iv = new byte[GCM_IV_BYTES];
        rng.nextBytes(iv);
        Cipher aes = Cipher.getInstance(AES_ALG);
        aes.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] aesCt = aes.doFinal(plaintext);

        Cipher rsa = Cipher.getInstance(RSA_ALG);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.ENCRYPT_MODE, publicKey, oaep);
        byte[] encKey = rsa.doFinal(aesKey.getEncoded());

        ByteBuffer buf = ByteBuffer.allocate(encKey.length + iv.length + aesCt.length);
        buf.put(encKey);
        buf.put(iv);
        buf.put(aesCt);

        return Base64.getEncoder().encodeToString(buf.array());
    }

    public PaymentInstruction decrypt(String base64Ct) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64Ct);
        if (all.length < RSA_KEY_BYTES + GCM_IV_BYTES + GCM_TAG_BITS / 8) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        byte[] encKey = new byte[RSA_KEY_BYTES];
        byte[] iv     = new byte[GCM_IV_BYTES];
        byte[] aesCt  = new byte[all.length - RSA_KEY_BYTES - GCM_IV_BYTES];

        ByteBuffer buf = ByteBuffer.wrap(all);
        buf.get(encKey);
        buf.get(iv);
        buf.get(aesCt);

        Cipher rsa = Cipher.getInstance(RSA_ALG);
        OAEPParameterSpec oaep = new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        rsa.init(Cipher.DECRYPT_MODE, serverKey.getPrivateKey(), oaep);
        byte[] aesKeyBytes = rsa.doFinal(encKey);
        SecretKey aesKey = new SecretKeySpec(aesKeyBytes, "AES");

        Cipher aes = Cipher.getInstance(AES_ALG);
        aes.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] plaintext = aes.doFinal(aesCt);

        return json.readValue(plaintext, PaymentInstruction.class);
    }

    /**
     * SHA-256 of ciphertext = idempotency key.
     * Two copies of same packet have identical ciphertexts → identical hashes.
     */
    public String hashCiphertext(String base64Ct) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(base64Ct.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    /** Hash UPI PIN for verification */
    public String hashPin(String pin) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha.digest(pin.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
