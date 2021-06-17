package org.openhab.binding.simplebinary.internal.core;

public class SimpleBinaryAddress {

    private final int deviceId;
    private final int address;
    private final SimpleBinaryTypes type;
    private final int length;

    /**
     * Construct fully described item address
     *
     * @param deviceId Device ID/address
     * @param address Item address in device
     * @param type Item data type
     * @param length Item data length
     */
    public SimpleBinaryAddress(int deviceId, int address, SimpleBinaryTypes type, int length) {
        this.deviceId = deviceId;
        this.address = address;
        this.type = type;
        this.length = length;
    }

    /**
     * Construct simple item type based address
     *
     * @param deviceId Device ID/address
     * @param address Item address in device
     * @param dataType Item data type
     */
    public SimpleBinaryAddress(int deviceId, int address, SimpleBinaryTypes dataType) {
        this(deviceId, address, dataType, 1);
    }

    /**
     * Construct simple item string type based address
     *
     * @param deviceId Device ID/address
     * @param address Item address in device
     * @param dataType Item data type
     */
    public SimpleBinaryAddress(int deviceId, int address, String dataType) {
        this(deviceId, address, resolveType(dataType), 1);
    }

    /**
     * Construct byte array/string item address
     *
     * @param deviceId Device ID/address
     * @param address Item address in device
     * @param length Item data length
     */
    public SimpleBinaryAddress(int deviceId, int address, int length) {
        this(deviceId, address, SimpleBinaryTypes.BYTE, length);
    }

    /**
     * Resolve string type representation
     *
     * @param dataType Item string data type
     * @return Converted data type
     */
    public static SimpleBinaryTypes resolveType(String dataType) {
        if (dataType.equalsIgnoreCase("byte")) {
            return SimpleBinaryTypes.BYTE;
        } else if (dataType.equalsIgnoreCase("word")) {
            return SimpleBinaryTypes.WORD;
        } else if (dataType.equalsIgnoreCase("dword")) {
            return SimpleBinaryTypes.DWORD;
        } else if (dataType.equalsIgnoreCase("float")) {
            return SimpleBinaryTypes.FLOAT;
        } else if (dataType.equalsIgnoreCase("rgb")) {
            return SimpleBinaryTypes.RGB;
        } else if (dataType.equalsIgnoreCase("rgbw")) {
            return SimpleBinaryTypes.RGBW;
        } else if (dataType.equalsIgnoreCase("hsb")) {
            return SimpleBinaryTypes.HSB;
        } else {
            return SimpleBinaryTypes.UNKNOWN;
        }
    }

    /**
     * Get item device ID
     *
     * @return
     */
    public int getDeviceId() {
        return deviceId;
    }

    /**
     * Get item address
     *
     * @return
     */
    public int getAddress() {
        return address;
    }

    /**
     * Get item data type
     *
     * @return
     */
    public SimpleBinaryTypes getType() {
        return type;
    }

    /**
     * Get item data length
     *
     * @return
     */
    public int getLength() {
        return length;
    }
}
