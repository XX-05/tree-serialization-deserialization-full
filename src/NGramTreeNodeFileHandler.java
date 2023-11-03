/*
File: NGramTreeNodeFileHandler.java
Copyright (c) August 18, 2023, Matthew Aharonian

Author: Matthew Aharonian
Created: August 18, 2023
Version: 1.0

Description:
NGramTreeNodeFileHandler is a utility class for handling serialization and
deserialization of NGramTreeNode objects. This class provides methods for
serializing NGramTreeNode trees into string and binary formats, as well as
for deserializing them from these formats. It includes hashing functions, tree
reconstruction algorithms, and exception handling for malformed binary data.
*/
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;


class Pair<A, B> {
    private A first;
    private B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() {
        return first;
    }

    public void setFirst(A newValue) {
        first = newValue;
    }

    public B getSecond() {
        return second;
    }

    public void setSecond(B newValue) {
        second = newValue;
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ")";
    }

    public boolean equals(Pair<?, ?> other) {
        if (other.first.getClass() != first.getClass())
            return false;
        if (other.second.getClass() != second.getClass())
            return false;

        if (!other.first.equals(first))
            return false;
        return other.second.equals(second);
    }
}

/**
 * Exception class used for signaling errors related to malformed binary serialization data.
 * This exception is typically thrown during the deserialization process when the binary data
 * cannot be properly parsed or does not conform to the expected format.
 */
class MalformedSerialBinaryException extends Exception {
    public MalformedSerialBinaryException(String msg) {
        super(msg);
    }
}

/**
 * A utility class for handling serialization and deserialization of NGramTreeNode objects.
 * Provides methods to serialize NGramTreeNode trees into string and binary formats,
 * as well as to deserialize them from these formats.
 */
public class NGramTreeNodeFileHandler {
    SerializationCodec codec;

    /**
     * Creates a new NGramTreeNodeFileHandler which uses the given serialization codec
     * to serialize and deserialize ngram trees.
     *
     * @param serializationCodec The codec to use for binary tree (de)serialization
     */
    public NGramTreeNodeFileHandler(SerializationCodec serializationCodec) {
        codec = serializationCodec;
    }

    /**
     * Creates a new NGramTreeNodeFileHandler which uses the default serialization codec
     * to serialize and deserialize files. See {@link SerializationCodec#DEFAULT_SERIALIZATION_CODEC}.
     */
    public NGramTreeNodeFileHandler() {
        this(SerializationCodec.DEFAULT_SERIALIZATION_CODEC);
    }

    /**
     * Computes the rolling hash of a given string, s.
     *
     * @param s The string to hash
     * @param power The power to use in the hash
     * @param modulo The maximum value of the hash
     * @return The rolling hash of s
     */
    static int rollingHash(String s, int power, int modulo) {
        int hash = 0;
        long p_pow = 1;
        for (char c : s.toCharArray()) {
            hash = (int) ((hash + (c - ' ' + 1) * p_pow) % modulo);
            p_pow = (p_pow * power) % modulo;
        }
        return hash;
    }

    /**
     * Computes the rolling hash of a given string, s.
     *
     * @return The rolling hash of s
    */
    int rollingHash(String s) {
        return rollingHash(s, 97, codec.BACKREFERENCE_ARRAY_SIZE);
    }

    /**
     * Serializes a given NGramTreeNode tree as a string.
     *
     * @param root The root node of the tree
     * @return The string representation of root
     */
    public String serialize(NGramTreeNode root) {
        StringBuilder flattened = new StringBuilder();
        Stack<NGramTreeNode> stack = new Stack<>();

        String[] backreferences = new String[codec.BACKREFERENCE_ARRAY_SIZE];

        stack.add(root);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            int nodeHash = rollingHash(node.getWord());

            String nodeInfo;
            if (node.getWord().equals(backreferences[nodeHash])) {
                nodeInfo = "}" + nodeHash;
            } else {
                backreferences[nodeHash] = node.getWord();
                nodeInfo = node.getWord();
            }

            nodeInfo += "|" + node.getChildren().length + "]";

            flattened.append(nodeInfo);
            stack.addAll(List.of(node.getChildren()));
        }

        return flattened.toString();
    }

    /**
     * Generates a binary header for the serialized tree file
     * storing the codec constants used for serialization & deserialization
     * as well as a magic header for file identification.
     *
     * @return A byte array containing the header bytes
     */
    static byte[] generateBinaryHeader(SerializationCodec codec) {
        return new byte[]{
                'n','t','s','f',
                (byte) codec.BACKREFERENCE_BYTE,
                (byte) codec.BACKREFERENCE_ARRAY_SIZE
        };
    }

    /**
     * Parses the header of a given serialized tree file and
     * returns the codec specified by it.
     *
     * @param fr An input stream reading the serialized tree file
     * @return The codec specified by the serialized tree file header
     * @throws IOException If there is an issue reading the file
     * @throws MalformedSerialBinaryException If the magic ntsf bytes are not found
     *      or the expected header size exceeds the actual file size
     */
    static SerializationCodec parseBinaryHeader(InputStream fr) throws IOException, MalformedSerialBinaryException {
        byte[] buff = new byte[6];
        if (fr.read(buff, 0, buff.length) < buff.length) {
            throw new MalformedSerialBinaryException("No header found: file too small.");
        }

        // ensure the magic 'ntsf' header is present
        if (buff[0] != 'n' || buff[1] != 't' || buff[2] != 's' || buff[3] != 'f') {
            throw new MalformedSerialBinaryException("Invalid header: no magic 'ntsf' bytes");
        }

        // elements 4 & 5 of the magic header store
        // the backreferenceByte and maxBackreference respectively
        return new SerializationCodec(buff[4] & 0xff, buff[5] & 0xff);
    }

    /**
     * Encodes a NGramTreeNode as a byte block
     * storing the node's word and its number of children.
     *
     * @param node The node to encode
     * @return A byte block storing the node's data.
     */
    byte[] encodeNodeBinary(NGramTreeNode node) {
        int nChildren = node.getChildrenCount();
        int nChildrenBytes = (int) Math.ceil(Math.log(nChildren + 1) / Math.log(2) / 8.0);
        byte[] wordBytes = node.getWord().getBytes(StandardCharsets.US_ASCII);

        byte[] encoded = new byte[wordBytes.length + nChildrenBytes + 1];
        System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);

        encoded[wordBytes.length] = (byte) (codec.END_WORD_RANGE_START + nChildrenBytes);

        // copy nChildren bytes into tail-end of encoded array
        for (int i = 0; i < nChildrenBytes; i ++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren & 0xff);
            nChildren = nChildren >> 8;
        }

        return encoded;
    }

    /**
     * Computes and returns the minimum number of bytes needed
     * to represent the given val. Note that this method
     * will return 0 for 0.
     *
     * @param val The integer value
     * @return The number of bytes needed to represent val
     */
    static int computeValByteSize(int val) {
        int minBytes = 0;
        while (val > 0) {
            minBytes ++;
            val = val >> 8;
        }
        return minBytes;
    }

    /**
     * Creates a special byte block to represent a given node.
     * This is used when the word of a node being serialized has already been
     * seen, allowing its word can be retrieved by a backreference. This generally
     * saves space by restricting the word data stored to only 2 bytes instead of
     * however long the word actually is.
     *
     * @param backreference The index of the node's word in the backreference array
     * @param nChildren The number of children the node has.
     * @return A new backreference byte block.
     */
    byte[] encodeNodeBackreferenceBinary(int backreference, int nChildren) {
        int nChildrenBytes = computeValByteSize(nChildren);

        byte[] encoded = new byte[3 + nChildrenBytes];

        // copy nChildren bytes into tail-end of encoded array
        for (int i = 0; i < nChildrenBytes; i ++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren & 0xff);
            nChildren = nChildren >> 8;
        }

        encoded[0] = (byte) codec.BACKREFERENCE_BYTE;

        byte idx = (byte) backreference;
        encoded[1] = idx;

        encoded[2] = (byte) (codec.END_WORD_RANGE_START + nChildrenBytes);

        return encoded;
    }

    /**
     * Serializes a given NGramTreeNode tree as a collection of byte blocks
     * representing the tree.
     *
     * @param root The root node in the tree
     * @param fw An OutputStream to write the serialized content to
     * @throws IOException If there is a problem writing to fw.
     */
    public void serializeBinary(NGramTreeNode root, OutputStream fw) throws IOException {
        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(root);

        String[] backreferences = new String[codec.BACKREFERENCE_ARRAY_SIZE];

        fw.write(generateBinaryHeader(codec));

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            int nodeHash = rollingHash(node.getWord());

            if (node.getWord().equals(backreferences[nodeHash])) {
                fw.write(encodeNodeBackreferenceBinary(nodeHash, node.getChildrenCount()));
            } else {
                backreferences[nodeHash] = node.getWord();
                fw.write(encodeNodeBinary(node));
            }
            stack.addAll(List.of(node.getChildren()));
        }
    }

    /**
     * The magic of the tree reconstruction algorithm:
     * deflateStack pops the previous node (parent) in the stack, adds the newNodeData to it,
     * reduces the parent's remaining children value by 1, then adds the parent and child data
     * back onto to the stack if their remaining children is greater than 0. After this,
     * the stack is 'deflated' and all nodes with 0 remaining children are removed from the top
     * of the stack until a node with > 0 remaining children is reached.
     *
     * @param stack The stack populated by previous deflateStack calls which stores the intermediate state
     *              of the node reconstruction.
     * @param newNodeData A Pair storing the new node to add and the number of children that must be attached
     *                    to it during reconstruction.
     */
    static void deflateStack(Stack<Pair<NGramTreeNode, Integer>> stack, Pair<NGramTreeNode, Integer> newNodeData) {
        Pair<NGramTreeNode, Integer> parentData = stack.get(stack.size() - 1);
        parentData.getFirst().addChild(newNodeData.getFirst());
        parentData.setSecond(parentData.getSecond() - 1);

        if (parentData.getSecond() == 0)
            // pop here instead of originally popping and reinserting to reduce unnecessary stack manipulation
            stack.pop();
        if (newNodeData.getSecond() > 0)
            stack.add(newNodeData);

        // ** deflate stack **
        for (int j = stack.size() - 1; j > 0; j--) {
            Pair<NGramTreeNode, Integer> n = stack.get(j);
            if (n.getSecond() > 0) {
                break;
            }
            stack.pop();
        }
    }

    /**
     * Deserializes a string representation of an NGramTree into a new NGramTreeNode object.
     *
     * @param serializedData The serialized NGramTreeNode string.
     * @return The NGramTreeNode reconstructed from the serialized data.
     */
    public NGramTreeNode deserialize(String serializedData) {
        Stack<Pair<NGramTreeNode, Integer>> stack = new Stack<>();

        String[] backreferences = new String[codec.BACKREFERENCE_ARRAY_SIZE];

        NGramTreeNode rootNode = null;

        StringBuilder buff = new StringBuilder();
        String letter = "";
        boolean isBackReference = false;

        for (int i = 0; i < serializedData.length(); i ++) {
            String currChar = serializedData.substring(i, i + 1);

            switch (currChar) {
                case "]" -> {
                    NGramTreeNode newNode = new NGramTreeNode(letter);
                    int nChildren = Integer.parseInt(buff.toString());
                    buff.setLength(0);
                    letter = "";
                    isBackReference = false;

                    int nodeHash = rollingHash(newNode.getWord());
                    backreferences[nodeHash] = newNode.getWord();

                    Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(newNode, nChildren);

                    if (rootNode == null) {
                        rootNode = newNode;
                        stack.add(newNodeData);
                        continue;
                    }

                    deflateStack(stack, newNodeData);
                }
                case "}" -> isBackReference = true;
                case "|" -> {
                    if (isBackReference) {
                        int idx = Integer.parseInt(buff.toString());
                        letter = backreferences[idx];
                    } else {
                        letter = buff.toString();
                    }
                    buff.setLength(0);
                }
                default -> buff.append(currChar);
            }
        }

        return rootNode;
    }

    /**
     * Converts an ArrayList of (character) bytes to a String.
     *
     * @param buff The ArrayList containing the byte data.
     * @return The String representation of the byte data.
     */
    static String parseBuffToString(ArrayList<Byte> buff) {
        StringBuilder word = new StringBuilder();
        for (Byte b : buff) {
            word.append((char) b.byteValue());
        }
        return word.toString();
    }

    /**
     * Parses the next nBytes of the file and returns the integer,
     * nChildren, that they store (big endian).
     *
     * @param fr The file input stream
     * @param nBytes The number of bytes storing nChildren
     * @return The parsed nChildren value
     * @throws IOException when there is an issue reading from fr
     */
    static int parseNChildren(InputStream fr, int nBytes) throws IOException {
        int nChildren = 0;
        for (int i = 0; i < nBytes; i++) {
            nChildren = (nChildren & 0xFF) << 8 | (fr.read() & 0xFF);
        }
        return nChildren;
    }

    /**
     * Deserializes a binary encoded NGramTreeNode from an InputStream.
     *
     * @param fr The InputStream containing the binary encoded data.
     * @return The root NGramTreeNode reconstructed from the binary encoded data.
     * @throws IOException If an I/O error occurs while reading from the stream.
     * @throws MalformedSerialBinaryException If the binary data is malformed or cannot be parsed.
     */
    public static NGramTreeNode deserializeBinary(InputStream fr) throws IOException, MalformedSerialBinaryException {
        NGramTreeNode rootNode = null;
        Stack<Pair<NGramTreeNode, Integer>> stack = new Stack<>();

        SerializationCodec codec = parseBinaryHeader(fr);

        String[] backreferences = new String[codec.BACKREFERENCE_ARRAY_SIZE];

        ArrayList<Byte> buff = new ArrayList<>();

        int currByte;
        while ((currByte = fr.read()) != -1) {
            if (currByte >= codec.BACKREFERENCE_BYTE) {
                // parses buff into word for the standard block case
                // if the current block is a backreference, word is set to an empty string
                String word = parseBuffToString(buff);

                if (currByte == codec.BACKREFERENCE_BYTE) {
                    int idx = fr.read() & 0xff;
                    word = backreferences[idx];
                    currByte = fr.read(); // read byte storing nChildren byte length to match the standard case
                }

                int nChildren = parseNChildren(fr, currByte - codec.END_WORD_RANGE_START);
                buff.clear();

                NGramTreeNode node = new NGramTreeNode(word);
                Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(node, nChildren);

                int nodeHash = rollingHash(word, 97, codec.BACKREFERENCE_ARRAY_SIZE);
                backreferences[nodeHash] = word;

                if (rootNode == null) {
                    rootNode = node;
                    stack.add(newNodeData);
                    continue;
                }

                deflateStack(stack, newNodeData);  // tree reconstruction magic
            } else {
                buff.add((byte) currByte);
            }
        }

        if (rootNode == null) {
            throw new MalformedSerialBinaryException("Could not parse any nodes from the given data!");
        }

        return rootNode;
    }
}
