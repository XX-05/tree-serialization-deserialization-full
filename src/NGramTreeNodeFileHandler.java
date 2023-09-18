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

class MalformedSerialBinaryException extends Exception {
    public MalformedSerialBinaryException(String msg) {
        super(msg);
    }
}


final class SerializationCodec {
    final static int BACKREFERENCE = 0xf1;
    final static int END_WORD_RANGE_START = BACKREFERENCE + 1;
    final static int MAX_BACKREFERENCE = 0xff;
}

public class NGramTreeNodeFileHandler {

    static int rollingHash(String s, int power, int modulo) {
        int hash = 0;
        long p_pow = 1;
        for (char c : s.toCharArray()) {
            hash = (int) ((hash + (c - ' ' + 1) * p_pow) % modulo);
            p_pow = (p_pow * power) % modulo;
        }
        return hash;
    }
    
    static int rollingHash(String s) {
        return rollingHash(s, 97, SerializationCodec.MAX_BACKREFERENCE);
    }

    public static String serialize(NGramTreeNode root) {
        StringBuilder flattened = new StringBuilder();
        Stack<NGramTreeNode> stack = new Stack<>();

        String[] backreferences = new String[SerializationCodec.MAX_BACKREFERENCE];

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

    static byte[] encodeNodeBinary(NGramTreeNode node) {
        int nChildren = node.getChildrenCount();
        int nChildrenBytes = (int) Math.ceil(Math.log(nChildren + 1) / Math.log(2) / 8.0);
        byte[] wordBytes = node.getWord().getBytes(StandardCharsets.US_ASCII);

        byte[] encoded = new byte[wordBytes.length + nChildrenBytes + 1];
        System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);

        encoded[wordBytes.length] = (byte) (SerializationCodec.END_WORD_RANGE_START + nChildrenBytes);

        // copy nChildren bytes into tail-end of encoded array
        for (int i = 0; i < nChildrenBytes; i ++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren & 0xff);
            nChildren = nChildren >> 8;
        }

        return encoded;
    }

    static byte[] encodeNodeBackreferenceBinary(int backreference, int nChildren) {
        int nChildrenBytes = (int) Math.ceil(Math.log(nChildren + 1) / Math.log(2) / 8.0);

        byte[] encoded = new byte[3 + nChildrenBytes];

        // copy nChildren bytes into tail-end of encoded array
        for (int i = 0; i < nChildrenBytes; i ++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren & 0xff);
            nChildren = nChildren >> 8;
        }

        encoded[0] = (byte) SerializationCodec.BACKREFERENCE;

        byte idx = (byte) backreference;
        encoded[1] = idx;

        encoded[2] = (byte) (SerializationCodec.END_WORD_RANGE_START + nChildrenBytes);

        return encoded;
    }

    public static void serializeBinary(NGramTreeNode root, OutputStream fw) throws IOException {
        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(root);

        String[] backreferences = new String[SerializationCodec.MAX_BACKREFERENCE];

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

    static void deflateStack(Stack<Pair<NGramTreeNode, Integer>> stack, Pair<NGramTreeNode, Integer> newNodeData) {
        Pair<NGramTreeNode, Integer> parentData = stack.pop();
        parentData.getFirst().addChild(newNodeData.getFirst());
        parentData.setSecond(parentData.getSecond() - 1);

        if (parentData.getSecond() > 0)
            stack.add(parentData);
        if (newNodeData.getSecond() > 0)
            stack.add(newNodeData);

        for (int j = stack.size() - 1; j > 0; j--) {
            Pair<NGramTreeNode, Integer> n = stack.get(j);
            if (n.getSecond() > 0) {
                break;
            }
            stack.pop();
        }
    }

    /**
     * Deserializes a serialized string representation of an NGramTree into a new NGramTreeNode object.
     *
     * @param serializedData The serialized NGramTreeNode string.
     * @return The NGramTreeNode reconstructed from the serialized data.
     */
    public static NGramTreeNode deserialize(String serializedData) {
        Stack<Pair<NGramTreeNode, Integer>> stack = new Stack<>();

        String[] backreferences = new String[SerializationCodec.MAX_BACKREFERENCE];

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
     * Converts an ArrayList of bytes to a String.
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
     * @throws IOException when there is an issue reading from fr.
     */
    static int parseNChildren(InputStream fr, int nBytes) throws IOException {
        int nChildren = 0;
        for (int i = 0; i < nBytes - SerializationCodec.END_WORD_RANGE_START; i++) {
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

        String[] backreferences = new String[SerializationCodec.MAX_BACKREFERENCE];

        ArrayList<Byte> buff = new ArrayList<>();

        int currByte;
        while ((currByte = fr.read()) != -1) {
            if (currByte >= SerializationCodec.BACKREFERENCE) {
                String word;
                if (currByte == SerializationCodec.BACKREFERENCE) {
                    int idx = fr.read() & 0xff;
                    word = backreferences[idx];
                    currByte = fr.read();
                } else {
                    word = parseBuffToString(buff);
                }
                int nChildren = parseNChildren(fr, currByte);
                buff.clear();

                NGramTreeNode node = new NGramTreeNode(word);
                Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(node, nChildren);

                int nodeHash = rollingHash(word);
                backreferences[nodeHash] = word;

                if (rootNode == null) {
                    rootNode = node;
                    stack.add(newNodeData);
                    continue;
                }

                deflateStack(stack, newNodeData);
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
