package org.openhab.binding.simplebinary.internal.core;

public class SimpleBinaryAddress {

    private final int deviceId;
    private final int address;
    private SimpleBinaryTypes type = SimpleBinaryTypes.UNKNOWN;
    private int length = 1;

    public SimpleBinaryAddress(int deviceId, int address) {
        this.deviceId = deviceId;
        this.address = address;
    }

    public SimpleBinaryAddress(int deviceId, int address, String dataType) {
        this(deviceId, address);

        if (dataType.equalsIgnoreCase("byte")) {
            type = SimpleBinaryTypes.BYTE;
        } else if (dataType.equalsIgnoreCase("word")) {
            type = SimpleBinaryTypes.WORD;
        } else if (dataType.equalsIgnoreCase("dword")) {
            type = SimpleBinaryTypes.DWORD;
        } else if (dataType.equalsIgnoreCase("float")) {
            type = SimpleBinaryTypes.FLOAT;
        } else if (dataType.equalsIgnoreCase("rgb")) {
            type = SimpleBinaryTypes.RGB;
        } else if (dataType.equalsIgnoreCase("rgbw")) {
            type = SimpleBinaryTypes.RGBW;
        } else if (dataType.equalsIgnoreCase("hsb")) {
            type = SimpleBinaryTypes.HSB;
        }
    }

    public SimpleBinaryAddress(int deviceId, int address, int length) {
        this(deviceId, address);

        this.length = length;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public int getAddress() {
        return address;
    }

    public SimpleBinaryTypes getType() {
        return type;
    }

    public int getLength() {
        return length;
    }
}
