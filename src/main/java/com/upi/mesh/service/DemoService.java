package com.upi.mesh.service;

import com.upi.mesh.crypto.HybridCryptoService;
import com.upi.mesh.crypto.ServerKeyHolder;
import com.upi.mesh.model.MeshPacket;
import com.upi.mesh.model.PaymentInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class DemoService {

    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;

    public MeshPacket createPacket(String senderVpa, String receiverVpa,
                                   BigDecimal amount, String pin, int ttl) throws Exception {
        PaymentInstruction instruction = new PaymentInstruction(
                senderVpa,
                receiverVpa,
                amount,
                crypto.hashPin(pin),
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli()
        );

        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(ciphertext);
        return packet;
    }
}
