import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;


class MalormedSerialBinaryException extends Exception {
    public MalormedSerialBinaryException(String msg) {
        super(msg);
    }
}
public class LetterTreeNodeFaulty {
    private final String letter;
    private final HashMap<String, LetterTreeNodeFaulty> children;

    static final class SerializationCODEC {
        final static int ENDNODE = 0xFF;
        final static int ENDWORD = 0xFE;
        final static int MAX_CHILDREN = 0xFFFF;
    }

    public LetterTreeNodeFaulty(String nodeLetter) {
        letter = nodeLetter;
        children = new HashMap<>();
    }

    public String toString() {
        StringBuilder childrenString = new StringBuilder();
        if (children.size() > 0) {
            for (LetterTreeNodeFaulty child : children.values()) {
                childrenString.append(child.toString()).append(", ");
            }
            childrenString.setLength(childrenString.length() - 2);
            return String.format("<LetterTreeNodeFaulty: %s; Children: %s>", letter, childrenString);
        } else {
            return String.format("<LetterTreeNodeFaulty: %s>", letter);
        }
    }

    public boolean deepEquals(LetterTreeNodeFaulty otherNode) {
        if (!this.letter.equals(otherNode.letter)) {
            return false;
        }

        if (this.children.size() != otherNode.children.size()) {
            return false;
        }

        for (Map.Entry<String, LetterTreeNodeFaulty> entry : this.children.entrySet()) {
            String key = entry.getKey();
            LetterTreeNodeFaulty thisChild = entry.getValue();
            LetterTreeNodeFaulty otherChild = otherNode.children.get(key);

            if (otherChild == null || !thisChild.deepEquals(otherChild)) {
                return false;
            }
        }

        return true;
    }

    public int countChildren() {
        Stack<LetterTreeNodeFaulty> stack = new Stack<>();
        stack.add(this);

        int nodesSeen = 0;
        while (!stack.isEmpty()) {
            LetterTreeNodeFaulty node = stack.pop();
            stack.addAll(node.children.values());
            nodesSeen ++;
        }

        return nodesSeen;
    }

    public LetterTreeNodeFaulty[] getChildren() {
        return children.values().toArray(new LetterTreeNodeFaulty[0]);
    }

    public int getChildrenCount() {
        return children.size();
    }

    public void addChild(LetterTreeNodeFaulty childNode) {
        children.put(childNode.letter, childNode);
    }

    public LetterTreeNodeFaulty addLetter(String letter) {
        LetterTreeNodeFaulty newNode = new LetterTreeNodeFaulty(letter.toUpperCase());
        addChild(newNode);
        return newNode;
    }

    private byte[] serializeNodeData(LetterTreeNodeFaulty node) {
        byte[] encoded = new byte[node.letter.length() + 4];
        int nChildren = node.children.size() % SerializationCODEC.MAX_CHILDREN;
        byte[] wordBytes = node.letter.getBytes(StandardCharsets.US_ASCII);

        System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);
        encoded[encoded.length - 4] = (byte) SerializationCODEC.ENDWORD;
        encoded[encoded.length - 3] = (byte) (nChildren >> 8);
        encoded[encoded.length - 2] = (byte) (nChildren);
        encoded[encoded.length - 1] = (byte) SerializationCODEC.ENDNODE;

        return encoded;
    }

    public void writeSerializedBin(OutputStream file) throws IOException {
        Stack<LetterTreeNodeFaulty> stack = new Stack<>();

        stack.add(this);

        while (!stack.isEmpty()) {
            LetterTreeNodeFaulty node = stack.pop();
            byte[] nodeBin = serializeNodeData(node);
            file.write(nodeBin);
            stack.addAll(node.children.values());
        }
    }

    public String serialize() {
        StringBuilder flattened = new StringBuilder();
        Stack<LetterTreeNodeFaulty> stack = new Stack<>();

        stack.add(this);

        while (!stack.isEmpty()) {
            LetterTreeNodeFaulty node = stack.pop();
            String nodeInfo = node.letter + "|" + node.children.size() + "]";
            flattened.append(nodeInfo);
            stack.addAll(node.children.values());
        }

        return flattened.toString();
    }

    static ArrayList<Pair<LetterTreeNodeFaulty, Integer>> deserializeFlattened(String serializedData) {
        ArrayList<Pair<LetterTreeNodeFaulty, Integer>> flattened = new ArrayList<>();

        StringBuilder buff = new StringBuilder();
        String letter = "";

        for (int i = 0; i < serializedData.length(); i ++) {
            String currChar = serializedData.substring(i, i + 1);

            switch (currChar) {
                case "]" -> {
                    LetterTreeNodeFaulty newNode = new LetterTreeNodeFaulty(letter);
                    int nChildren = Integer.parseInt(buff.toString());
                    flattened.add(new Pair<>(newNode, nChildren));
                    buff.setLength(0);
                    letter = "";
                }
                case "|" -> {
                    letter = buff.toString();
                    buff.setLength(0);
                }
                default -> buff.append(currChar);
            }
        }

        return flattened;
    }

    private static byte[] concatByteArrays(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static int byteArrayToInt(byte[] bytes) {
        int value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    static ArrayList<Pair<LetterTreeNodeFaulty, Integer>> deserializeBinFileFlattened(InputStream file) throws IOException {
        ArrayList<Pair<LetterTreeNodeFaulty, Integer>> flattened = new ArrayList<>();

        byte[] buff = new byte[0];
        String word = "";

        int processedNodes = 0;

        int currByte;
        while ((currByte = file.read()) != -1) {
            switch (currByte) {
                case SerializationCODEC.ENDNODE -> {
                    processedNodes ++;
                    LetterTreeNodeFaulty newNode = new LetterTreeNodeFaulty(word);
                    int nChildren = byteArrayToInt(buff);
                    flattened.add(new Pair<>(newNode, nChildren));
                    buff = new byte[0];
                    word = "";
                }
                case SerializationCODEC.ENDWORD -> {
                    word = new String(Base64.getDecoder().decode(buff), StandardCharsets.US_ASCII);
                    buff = new byte[0];
                }
                default -> buff = concatByteArrays(buff, new byte[]{(byte)currByte});
            }
        }

        System.out.println(processedNodes);

        return flattened;
    }

    public static LetterTreeNodeFaulty deserializeFile(FileInputStream file) throws MalormedSerialBinaryException, IOException {
        Stack<Pair<LetterTreeNodeFaulty, Integer>> stack = new Stack<>();
        ArrayList<Pair<LetterTreeNodeFaulty, Integer>> flattened = deserializeBinFileFlattened(file);

        if (flattened.isEmpty()) {
            throw new MalormedSerialBinaryException("No data or improperly formatted data!");
        }

        LetterTreeNodeFaulty rootNode = flattened.get(0).getFirst();
        stack.add(flattened.get(0));
        flattened.remove(0);

        for (Pair<LetterTreeNodeFaulty, Integer> nodeData : flattened) {
//            System.out.println(stack.size());
            Pair<LetterTreeNodeFaulty, Integer> parentData = stack.pop();
            parentData.getFirst().addChild(nodeData.getFirst());
            parentData.setSecond(parentData.getSecond() - 1);

            stack.add(parentData);
            stack.add(nodeData);

            for (int i = stack.size() - 1; i > 0; i --) {
                Pair<LetterTreeNodeFaulty, Integer> n = stack.get(i);
                if (n.getSecond() > 0) {
                    break;
                }
                stack.pop();
            }
//            System.out.println(stack.size());
        }

        return rootNode;
    }

    public static void main(String[] args) throws IOException, MalormedSerialBinaryException {
//        LetterTreeNodeFaulty tree = RandomAlphabetTreeGenerator.generateRandomAlphabetTree();
//
//        byte[] serializedNodeBytes = tree.serializeNodeData(tree);

//        try (FileOutputStream file = new FileOutputStream("tree.ngrams")) {
//            tree.writeSerializedBin(file);
//        }

        LetterTreeNodeFaulty deserialized;
        try (FileInputStream file = new FileInputStream("serialized.bin.ngrams")) {
            deserialized = LetterTreeNodeFaulty.deserializeFile(file);
        }

//        System.out.println(tree.deepEquals(tree));
//        System.out.println(tree.deepEquals(deserialized));
        System.out.println(deserialized.countChildren());
        System.out.println(deserialized.children.size());
    }
}
