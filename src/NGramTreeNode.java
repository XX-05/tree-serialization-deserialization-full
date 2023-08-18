import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

final class SerializationCODEC {
    final static int END_WORD_RANGE_START = 0xF0;
    final static int END_WORD_1B = END_WORD_RANGE_START + 1;
    final static int END_WORD_2B = END_WORD_RANGE_START + 2;
}

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
}


public class NGramTreeNode {
    private final String word;
    private final HashMap<String, NGramTreeNode> children;

    public NGramTreeNode(String nodeWord) {
        word = nodeWord;
        children = new HashMap<>();
    }

    public String getWord() {
        return word;
    }

    public String toString() {
        StringBuilder childrenString = new StringBuilder();
        if (children.size() > 0) {
            for (NGramTreeNode child : children.values()) {
                childrenString.append(child.toString()).append(", ");
            }
            childrenString.setLength(childrenString.length() - 2);
            return String.format("<LetterTreeNode: %s; Children: %s>", word, childrenString);
        } else {
            return String.format("<LetterTreeNode: %s>", word);
        }
    }

    public boolean deepEquals(NGramTreeNode otherNode) {
        if (!this.word.equals(otherNode.word)) {
            return false;
        }

        if (this.children.size() != otherNode.children.size()) {
            return false;
        }

        for (Map.Entry<String, NGramTreeNode> entry : this.children.entrySet()) {
            String key = entry.getKey();
            NGramTreeNode thisChild = entry.getValue();
            NGramTreeNode otherChild = otherNode.children.get(key);

            if (otherChild == null || !thisChild.deepEquals(otherChild)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a deep count of how many nodes are in the tree
     * branching off of this node (including this node).
     *
     * @return The total number of nodes attached to this node.
     */
    public int branchSize() {
        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(this);

        int nodesSeen = 0;
        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            stack.addAll(node.children.values());
            nodesSeen ++;
        }

        return nodesSeen;
    }

    public NGramTreeNode[] getChildren() {
        return children.values().toArray(new NGramTreeNode[0]);
    }

    /**
     * Returns the number of children directly attached to this node.
     *
     * @return The number of children directly attached to this node.
     */
    public int getChildrenCount() {
        return children.size();
    }

    public void addChild(NGramTreeNode childNode) {
        children.put(childNode.word, childNode);
    }

    public NGramTreeNode addWord(String word) {
        if (children.containsKey(word))
            return children.get(word);

        NGramTreeNode newNode = new NGramTreeNode(word.toLowerCase());
        addChild(newNode);
        return newNode;
    }

    /**
     * Creates a branch off this node populated
     * with words from the provided n-gram.
     *
     * @param nGram An ordered array of words in the n-gram to add.
     */
    public void addNGram(String[] nGram) {
        NGramTreeNode node = this;
        for (String word : nGram) {
            node = node.addWord(word);
        }
    }

    /**
     * Given an ordered array of words in an n-gram,
     * returns an array of guesses for the next word in the sequence.
     *
     * @param nGram An ordered array of words in the n-gram sequence.
     * @return Predictions for the next word in the sequence.
     */
    public String[] predictNextWord(String[] nGram) {
        NGramTreeNode node = this;

        for (String word : nGram) {
            if (node.children.size() == 0 || word.length() == 0) {
                break;
            }

            String nextChildWord = LevenshteinDistance.findClosestString(word, node.children.keySet().toArray(new String[0]));
            node = node.children.get(nextChildWord);
        }

        return node.children.keySet().toArray(new String[0]);
    }

    public String serialize() {
        StringBuilder flattened = new StringBuilder();
        Stack<NGramTreeNode> stack = new Stack<>();

        stack.add(this);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            String nodeInfo = node.word + "|" + node.children.size() + "]";
            flattened.append(nodeInfo);
            stack.addAll(node.children.values());
        }

        return flattened.toString();
    }

    static byte[] encodeNode(NGramTreeNode node) {
        int nChildren = node.children.size() % LetterTreeNodeFaulty.SerializationCODEC.MAX_CHILDREN;
        byte[] wordBytes = node.word.getBytes(StandardCharsets.US_ASCII);

        byte[] encoded;
        if (nChildren <= 0xFF) {
            encoded = new byte[wordBytes.length + 2];

            System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);
            encoded[encoded.length - 2] = (byte) SerializationCODEC.END_WORD_1B;
        } else {
            encoded = new byte[wordBytes.length + 3];

            System.arraycopy(wordBytes, 0, encoded, 0, wordBytes.length);
            encoded[encoded.length - 3] = (byte) SerializationCODEC.END_WORD_2B;
            encoded[encoded.length - 2] = (byte) (nChildren >> 8);
        }

        encoded[encoded.length - 1] = (byte) (nChildren & 0xff);

        return encoded;
    }

    static Pair<NGramTreeNode, Integer> decodeNode(byte[] encodedNode) throws DeserializationException {
        ArrayList<Byte> buff = new ArrayList<>();
        NGramTreeNode node;
        int idx = 0;
        while (idx < encodedNode.length)  {
            int currByte = encodedNode[idx] & 0xFF;
            if (currByte > SerializationCODEC.END_WORD_RANGE_START) {
                int nChildren = 0;
                for (int i = 0; i < currByte - SerializationCODEC.END_WORD_RANGE_START; i ++) {
                    idx ++;
                    nChildren = (nChildren & 0xFF) << 8 | (encodedNode[idx] & 0xFF);
                }
                String word = parseBuffToString(buff);
                buff.clear();

                node = new NGramTreeNode(word);
                return new Pair<>(node, nChildren);
            } else {
                buff.add((byte) currByte);
            }
            idx ++;
        }
        throw new DeserializationException("Could not parse node encoding");
    }

    public void serializeBinary(OutputStream fw) throws IOException {
        Stack<NGramTreeNode> stack = new Stack<>();
        stack.add(this);

        while (!stack.isEmpty()) {
            NGramTreeNode node = stack.pop();
            fw.write(encodeNode(node));
            stack.addAll(node.children.values());
        }
    }

    static void cascadeDeflateStack(Stack<Pair<NGramTreeNode, Integer>> stack, Pair<NGramTreeNode, Integer> newNodeData) {
        Pair<NGramTreeNode, Integer> parentData = stack.pop();
        parentData.getFirst().addChild(newNodeData.getFirst());
        parentData.setSecond(parentData.getSecond() - 1);

        stack.add(parentData);
        stack.add(newNodeData);

        for (int j = stack.size() - 1; j > 0; j--) {
            Pair<NGramTreeNode, Integer> n = stack.get(j);
            if (n.getSecond() > 0) {
                break;
            }
            stack.pop();
        }
    }

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

    static String parseBuffToString(ArrayList<Byte> buff) {
        StringBuilder word = new StringBuilder();
        for (Byte b : buff) {
            word.append((char) b.byteValue());
        }
        return word.toString();
    }

    public static NGramTreeNode deserializeBinary(InputStream fr) throws IOException, DeserializationException {
        NGramTreeNode rootNode = null;
        Stack<Pair<NGramTreeNode, Integer>> stack = new Stack<>();

        ArrayList<Byte> buff = new ArrayList<>();

        int currByte;
        while ((currByte = fr.read()) != -1) {
            if (currByte > SerializationCODEC.END_WORD_RANGE_START) {
                int nChildren = 0;
                for (int i = 0; i < currByte - SerializationCODEC.END_WORD_RANGE_START; i ++) {
                    nChildren = (nChildren & 0xFF) << 8 | (fr.read() & 0xFF);
                }

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
            } else {
                buff.add((byte) currByte);
            }
        }

        if (rootNode == null) {
            throw new DeserializationException("Could not parse any nodes from the given data!");
        }

        return rootNode;
    }

    void addCorpus(String corpusDir, int nGramLength) {
        try {
            for (Path f: Files.walk(Paths.get(corpusDir)).filter(Files::isRegularFile).toList()) {
                String[] tokens = Main.readFile(f.toString()).split(" ");

                for (int i = 0; i < tokens.length - nGramLength + 1; i += nGramLength) {
                    String[] ngram = new String[nGramLength];
                    System.arraycopy(tokens, i, ngram, 0, nGramLength);
                    addNGram(ngram);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
