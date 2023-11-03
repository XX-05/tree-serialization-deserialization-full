/*
File: SerializationCodec.java
Copyright (c) November 2, 2023, Matthew Aharonian

Author: Matthew Aharonian
Created: November 2, 2023
Version: 1.0

Description:
SerializationCodec is a utility class for handling serialization and
deserialization of NGramTreeNode objects. This class stores constants
used during the serialization and deserialization processes.
*/

/**
 * Constants used for binary serialization and deserialization of NGramTreeNode objects.
 */
public class SerializationCodec {
    public final int BACKREFERENCE_BYTE;
    public final int END_WORD_RANGE_START;
    public final int BACKREFERENCE_ARRAY_SIZE;

    /**
     * Creates a SerializationCodec with the given backreference byte and maximum backreference values.
     *
     * @param backreferenceByte The backreference byte value
     * @param backreferenceArraySize The maximum backreference value
     */
    public SerializationCodec(int backreferenceByte, int backreferenceArraySize) {
        BACKREFERENCE_BYTE = backreferenceByte;
        BACKREFERENCE_ARRAY_SIZE = backreferenceArraySize;

        END_WORD_RANGE_START = backreferenceByte + 1;
    }

    public static final SerializationCodec DEFAULT_SERIALIZATION_CODEC = new SerializationCodec(0xf0, 255);
}
