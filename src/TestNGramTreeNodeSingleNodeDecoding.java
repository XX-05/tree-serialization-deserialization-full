import java.io.*;
import java.util.Arrays;

public class TestNGramTreeNodeSingleNodeDecoding {
    public static void testBinarySerializationDeserialization(NGramTreeNode root) throws IOException, MalormedSerialBinaryException {
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
        System.out.println(deserialized.branchSize());
    }
    public static void main(String[] args) throws IOException, MalormedSerialBinaryException {
        String serialized = Main.readFile("serialized.ngrams");
        NGramTreeNode root = NGramTreeNode.deserialize(serialized);

        System.out.println(Arrays.toString(root.predictNextWord("hi my name is".split(" "))));

        System.out.println(root.branchSize());
        testBinarySerializationDeserialization(root);
    }
}
