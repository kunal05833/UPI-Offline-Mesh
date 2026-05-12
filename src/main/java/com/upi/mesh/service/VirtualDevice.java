package com.upi.mesh.service;

import com.upi.mesh.model.MeshPacket;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualDevice {
    private final String deviceId;
    private final boolean hasInternet;
    private final Map<String, MeshPacket> held = new ConcurrentHashMap<>();

    public VirtualDevice(String deviceId, boolean hasInternet) {
        this.deviceId = deviceId;
        this.hasInternet = hasInternet;
    }

    public String getDeviceId()               { return deviceId; }
    public boolean hasInternet()              { return hasInternet; }
    public void hold(MeshPacket p)            { held.putIfAbsent(p.getPacketId(), p); }
    public Collection<MeshPacket> getHeldPackets() { return held.values(); }
    public boolean holds(String id)           { return held.containsKey(id); }
    public int packetCount()                  { return held.size(); }
    public void clear()                       { held.clear(); }
}
