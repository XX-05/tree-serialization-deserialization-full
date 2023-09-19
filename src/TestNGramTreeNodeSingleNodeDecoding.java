import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class TestNGramTreeNodeSingleNodeDecoding {
    public static void testPairEquals() {
        Pair<Integer, Integer> p1 = new Pair<>(1, 2);
        Pair<Integer, Integer> p2 = new Pair<>(1, 2);
        Pair<Integer, Integer> p3 = new Pair<>(1, 1);

        assert p1.equals(p2);
        assert !p1.equals(p3);

        Pair<String, String> p4 = new Pair<>("hi", "lo");
        Pair<String, String> p5 = new Pair<>("hi", "lo");
        Pair<String, String> p6 = new Pair<>("bonjour", "lo");

        assert p4.equals(p5);
        assert !p4.equals(p6);
    }

    public static void testBinarySerializationDeserialization(NGramTreeNode root) throws IOException, MalformedSerialBinaryException {
        ByteArrayOutputStream fw = new ByteArrayOutputStream();
        NGramTreeNodeFileHandler.serializeBinary(root, fw);

        ByteArrayInputStream fr = new ByteArrayInputStream(fw.toByteArray());
        NGramTreeNode deserialized = NGramTreeNodeFileHandler.deserializeBinary(fr);

        assert deserialized.branchSize() == root.branchSize();
        assert root.deepEquals(deserialized);

        System.out.println(Arrays.toString(deserialized.predictNextWord("hi my name is".split(" "))));
    }


    public static void testPlainTextSerializationDeserialization(NGramTreeNode root) {
        String serialized = NGramTreeNodeFileHandler.serialize(root);
        NGramTreeNode deserialized = NGramTreeNodeFileHandler.deserialize(serialized);

        assert deserialized.branchSize() == root.branchSize();
        assert root.deepEquals(deserialized);

        System.out.println(Arrays.toString(deserialized.predictNextWord("hi my name is".split(" "))));
    }

    public static ArrayList<Pair<Integer, Integer>> computeBackreferences(NGramTreeNode root) {
        ArrayList<Pair<Integer, Integer>> backreferences = new ArrayList<>();
        Stack<NGramTreeNode> stack = new Stack<>();

        String[] lookupTable = new String[SerializationCodec.MAX_BACKREFERENCE];

        stack.add(root);

        int processed = 0;

        while (!stack.isEmpty()) {
            processed ++;

            NGramTreeNode node = stack.pop();
            int nodeHash = NGramTreeNodeFileHandler.rollingHash(node.getWord());

            if (node.getWord().equals(lookupTable[nodeHash])) {
                backreferences.add(new Pair<>(processed, nodeHash));
            } else {
                lookupTable[nodeHash] = node.getWord();
            }

            stack.addAll(List.of(node.getChildren()));
        }

        return backreferences;
    }

    private static ArrayList<Pair<Integer, Integer>> parseBackreferencesPlainText(String serializedData) {
        ArrayList<Pair<Integer, Integer>> backreferences = new ArrayList<>();
        String[] lookupTable = new String[SerializationCodec.MAX_BACKREFERENCE];

        StringBuilder buff = new StringBuilder();
        String letter = "";
        boolean isBackReference = false;

        int parsed = 0;

        for (int i = 0; i < serializedData.length(); i ++) {
            String currChar = serializedData.substring(i, i + 1);

            switch (currChar) {
                case "]" -> {
                    NGramTreeNode newNode = new NGramTreeNode(letter);
                    buff.setLength(0);
                    letter = "";
                    isBackReference = false;

                    int nodeHash = NGramTreeNodeFileHandler.rollingHash(newNode.getWord());
                    lookupTable[nodeHash] = newNode.getWord();

                    parsed ++;
                }
                case "}" -> isBackReference = true;
                case "|" -> {
                    if (isBackReference) {
                        int idx = Integer.parseInt(buff.toString());
                        letter = lookupTable[idx];

                        backreferences.add(new Pair<>(parsed + 1, idx));
                    } else {
                        letter = buff.toString();
                    }
                    buff.setLength(0);
                }
                default -> buff.append(currChar);
            }
        }

        return backreferences;
    }

    private static ArrayList<Pair<Integer, Integer>> parseBackreferencesBinary(InputStream fr) throws IOException {
        ArrayList<Pair<Integer, Integer>> backreferences = new ArrayList<>();
        String[] lookupTable = new String[SerializationCodec.MAX_BACKREFERENCE];

        ArrayList<Byte> buff = new ArrayList<>();
        int parsed = 0;

        int currByte;
        while ((currByte = fr.read()) != -1) {
            if (currByte >= SerializationCodec.BACKREFERENCE) {
                parsed ++;
                String word;
                if (currByte == SerializationCodec.BACKREFERENCE) {
                    int idx = fr.read() & 0xff;
                    word = lookupTable[idx];
                    currByte = fr.read();

                    backreferences.add(new Pair<>(parsed, idx));
                } else {
                    word = NGramTreeNodeFileHandler.parseBuffToString(buff);
                }
                NGramTreeNodeFileHandler.parseNChildren(fr, currByte);
                buff.clear();

                int nodeHash = NGramTreeNodeFileHandler.rollingHash(word);
                lookupTable[nodeHash] = word;

                continue;
            }

            buff.add((byte) currByte);
        }

        return backreferences;
    }

    public static void testComputeBackreferences(NGramTreeNode root) throws IOException {
        ArrayList<Pair<Integer, Integer>> backreferences = computeBackreferences(root);

        ArrayList<Pair<Integer, Integer>> backreferencesTxt = parseBackreferencesPlainText(NGramTreeNodeFileHandler.serialize(root));

        ByteArrayOutputStream fw = new ByteArrayOutputStream();
        NGramTreeNodeFileHandler.serializeBinary(root, fw);
        ByteArrayInputStream fr = new ByteArrayInputStream(fw.toByteArray());
        ArrayList<Pair<Integer, Integer>> backreferencesBin = parseBackreferencesBinary(fr);

        assert backreferencesTxt.size() == backreferencesBin.size() && backreferencesBin.size() == backreferences.size();

        for (int i = 0; i < backreferences.size(); i ++) { // compare parsed txt backreferences vs computed
            assert backreferences.get(i).equals(backreferencesTxt.get(i));
        }

        for (int i = 0; i < backreferences.size(); i ++) { // compare parsed bin backreferences vs computed
            assert backreferences.get(i).equals(backreferencesBin.get(i));
        }
    }

    private static void testNChildrenBytesCompute() {
        for (int i = 0; i < 15000; i ++) {
            int b1 = (int) Math.ceil(Math.log(i + 1) / Math.log(2) / 8.0);
            int b2 = 0;
            int ic = i;
            while (ic > 0) {
                b2 += 1;
                ic = ic >> 8;
            }
            if (b1 != b2) {
                System.out.println("original: " + b1 + "new: " + b2 + "i: " + i);
            }
        }
    }

    private static String readFile(String fileName) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(fileName));
        StringBuilder stringBuilder = new StringBuilder();
        char[] buffer = new char[10];
        while (reader.read(buffer) != -1) {
            stringBuilder.append(new String(buffer));
            buffer = new char[10];
        }
        reader.close();

        return stringBuilder.toString();
    }

    public static void main(String[] args) throws IOException, MalformedSerialBinaryException {
        testNChildrenBytesCompute();
        testPairEquals();

        String serialized = readFile("serialized.ngrams");
        NGramTreeNode root = NGramTreeNodeFileHandler.deserialize(serialized);

        System.out.println(Arrays.toString(root.predictNextWord("hi my name is".split(" "))));

        System.out.println(root.branchSize());

        testComputeBackreferences(root);

        long startTime = System.nanoTime();
        testPlainTextSerializationDeserialization(root);
        long endTime = System.nanoTime();

        long duration = endTime - startTime;
        double milliseconds = (double) duration / 1_000_000; // Convert nanoseconds to milliseconds

        System.out.println("plaintext (de)serialization time: " + milliseconds + " ms");

        startTime = System.nanoTime();
        testBinarySerializationDeserialization(root);
        endTime = System.nanoTime();

        duration = endTime - startTime;
        milliseconds = (double) duration / 1_000_000; // Convert nanoseconds to milliseconds

        System.out.println("binary (de)serialization time: " + milliseconds + " ms");

        try (FileOutputStream fw = new FileOutputStream("lookups256.bin.ngrams")) {
            NGramTreeNodeFileHandler.serializeBinary(root, fw);
        }
    }
}
