import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class TestNGramTreeNodeSingleNodeDecoding {
    public static void testBinarySerializationDeserialization(NGramTreeNode root) throws IOException, MalformedSerialBinaryException {
        try (FileOutputStream fw = new FileOutputStream("test.bin.ngrams")) {
            NGramTreeNodeFileHandler.serializeBinary(root, fw);
        }

        NGramTreeNode deserialized;
        try (FileInputStream fr = new FileInputStream("test.bin.ngrams")) {
            deserialized = NGramTreeNodeFileHandler.deserializeBinary(fr);
        }

        assert deserialized.branchSize() == root.branchSize();
        assert root.deepEquals(deserialized);

        System.out.println(Arrays.toString(deserialized.predictNextWord("hi my name is".split(" "))));
        System.out.println(deserialized.branchSize());
    }


    public static void testPlainTextSerializationDeserialization(NGramTreeNode root) throws IOException, MalformedSerialBinaryException {
        Path serialPath = Path.of("test.ngrams");
        Files.writeString(serialPath, NGramTreeNodeFileHandler.serialize(root), StandardCharsets.US_ASCII);

        NGramTreeNode deserialized;
        String serialized = Files.readString(serialPath);
        deserialized = NGramTreeNodeFileHandler.deserialize(serialized);

        assert deserialized.branchSize() == root.branchSize();
        assert root.deepEquals(deserialized);

        System.out.println(Arrays.toString(deserialized.predictNextWord("hi my name is".split(" "))));
        System.out.println(deserialized.branchSize());
    }


    public static void main(String[] args) throws IOException, MalformedSerialBinaryException {
        String serialized = Main.readFile("serialized.ngrams");
        NGramTreeNode root = NGramTreeNodeFileHandler.deserialize(serialized);

        System.out.println(Arrays.toString(root.predictNextWord("hi my name is".split(" "))));

        System.out.println(root.branchSize());

        long startTime = System.nanoTime();
        testBinarySerializationDeserialization(root);
        long endTime = System.nanoTime();

        long duration = endTime - startTime;
        double milliseconds = (double) duration / 1_000_000_000; // Convert nanoseconds to milliseconds

        System.out.println("Method execution time: " + milliseconds + " ms");

        startTime = System.nanoTime();
        testPlainTextSerializationDeserialization(root);
        endTime = System.nanoTime();

        duration = endTime - startTime;
        milliseconds = (double) duration / 1_000_000_000; // Convert nanoseconds to milliseconds

        System.out.println("Method execution time: " + milliseconds + " ms");
    }
}
