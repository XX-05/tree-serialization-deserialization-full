import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class TestNGramTreeNodeSingleNodeDecoding {
    static NGramTreeNode deserializeBinary(InputStream fr) throws IOException {
        NGramTreeNode rootNode = null;
        Stack<Pair<NGramTreeNode, Integer>> stack = new Stack<>();

        ArrayList<Byte> buff = new ArrayList<>();

        int currByte;
        while ((currByte = fr.read()) != -1) {
            switch (currByte) {
                case SerializationCODEC.END_WORD_1B -> {
                    // to be implemented
                    System.out.println(currByte);
                }
                case SerializationCODEC.END_WORD_2B -> {
                    int nChildren = (fr.read() & 0xFF) << 8 | (fr.read() & 0xFF);
                    String word = NGramTreeNode.parseBuffToString(buff);
                    buff.clear();

                    NGramTreeNode node = new NGramTreeNode(word);
                    Pair<NGramTreeNode, Integer> newNodeData = new Pair<>(node, nChildren);

                    if (rootNode == null) {
                        rootNode = node;
                        stack.add(newNodeData);
                        continue;
                    }

                    NGramTreeNode.cascadeDeflateStack(stack, newNodeData);
                }
                default -> {
                    buff.add((byte) currByte);
                }
            }
        }

        return rootNode;
    }

    static void testBinaryEncodeDecode(NGramTreeNode root) throws DeserializationException {
        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(root);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            byte[] encoded = NGramTreeNode.encodeNode(node);
            Pair<NGramTreeNode, Integer> decoded = NGramTreeNode.decodeNode(encoded);
            assert decoded.getFirst().getWord().equals(node.getWord());
            assert decoded.getSecond() == node.getChildrenCount();
            stack.addAll(List.of(node.getChildren()));
        }
    }

    public static void testBinarySerializationDeserialization(NGramTreeNode root) throws IOException, DeserializationException {
        try (FileOutputStream fw = new FileOutputStream("test.bin.ngrams")) {
            root.serializeBinary(fw);
        }

        NGramTreeNode deserialized;
        try (FileInputStream fr = new FileInputStream("test.bin.ngrams")) {
            deserialized = NGramTreeNode.deserializeBinary(fr);
        }

        assert deserialized.branchSize() == root.branchSize();
        assert root.deepEquals(deserialized);

        System.out.println(Arrays.toString(deserialized.predictNextWord("hi my name is".split(" "))));
    }
    public static void main(String[] args) throws IOException, DeserializationException {
        String serialized = Main.readFile("serialized.ngrams");
        NGramTreeNode root = NGramTreeNode.deserialize(serialized);

        System.out.println(Arrays.toString(root.predictNextWord("hi my name is".split(" "))));

        System.out.println(root.branchSize());
        testBinaryEncodeDecode(root);
        testBinarySerializationDeserialization(root);
    }
}
