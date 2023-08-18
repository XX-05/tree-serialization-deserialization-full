import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class NGramTreeNodeFileHandler {

    public String serialize(NGramTreeNode root) {
        StringBuilder flattened = new StringBuilder();
        Stack<NGramTreeNode> stack = new Stack<>();

        stack.add(root);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            String nodeInfo = node.getWord() + "|" + node.getChildren().length + "]";
            flattened.append(nodeInfo);
            stack.addAll(List.of(node.getChildren()));
        }

        return flattened.toString();
    }

    static byte[] encodeNodeBinary(NGramTreeNode node) {
        int nChildren = node.getChildren().length;
        int nChildrenBytes = Math.max((int) Math.ceil(Math.log(nChildren + 1) / Math.log(2) / 8.0), 1);
        byte[] wordBytes = node.getWord().getBytes(StandardCharsets.US_ASCII);

        byte[] encoded = new byte[wordBytes.length + nChildrenBytes + 1];
        System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);

        encoded[wordBytes.length] = (byte) (SerializationCodec.END_WORD_RANGE_START + nChildrenBytes);

        for (int i = 0; i < nChildrenBytes; i ++) {
            encoded[encoded.length - i - 1] = (byte) (nChildren & 0xff);
            nChildren = nChildren >> 8;
        }

        return encoded;
    }

    public static void serializeBinary(NGramTreeNode root, OutputStream fw) throws IOException {
        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(root);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            fw.write(encodeNodeBinary(node));
            stack.addAll(List.of(node.getChildren()));
        }
    }

    static void cascadeDeflateStack(Stack<Pair<NGramTreeNode, Integer>> stack, Pair<NGramTreeNode, Integer> newNodeData) {
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

        NGramTreeNode rootNode = null;

        StringBuilder buff = new StringBuilder();
        String letter = "";

        for (int i = 0; i < serializedData.length(); i ++) {
            String currChar = serializedData.substring(i, i + 1);

            switch (currChar) {
                case "]" -> {
                    NGramTreeNode newNode = new NGramTreeNode(letter);
                    int nChildren = Integer.parseInt(buff.toString());
                    buff.setLength(0);
                    letter = "";

                    Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(newNode, nChildren);

                    if (rootNode == null) {
                        rootNode = newNode;
                        stack.add(newNodeData);
                        continue;
                    }

                    cascadeDeflateStack(stack, newNodeData);
                }
                case "|" -> {
                    letter = buff.toString();
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

    private static int parseNChildren(InputStream fr, int nBytes) throws IOException {
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

        ArrayList<Byte> buff = new ArrayList<>();

        int currByte;
        while ((currByte = fr.read()) != -1) {
            if (currByte > SerializationCodec.END_WORD_RANGE_START) {
                int nChildren = parseNChildren(fr, currByte);
                String word = parseBuffToString(buff);
                buff.clear();

                NGramTreeNode node = new NGramTreeNode(word);
                Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(node, nChildren);

                if (rootNode == null) {
                    rootNode = node;
                    stack.add(newNodeData);
                    continue;
                }

                cascadeDeflateStack(stack, newNodeData);
                continue;
            }

            buff.add((byte) currByte);
        }

        if (rootNode == null) {
            throw new MalformedSerialBinaryException("Could not parse any nodes from the given data!");
        }

        return rootNode;
    }
}
