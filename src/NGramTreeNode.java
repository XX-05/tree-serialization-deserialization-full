import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Stack;
import java.util.HashMap;

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

    public int countChildren() {
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

                    if (rootNode == null) {
                        rootNode = newNode;
                        stack.add(new Pair<>(newNode, nChildren));
                        continue;
                    }

                    Pair<NGramTreeNode, Integer> parentData = stack.pop();
                    parentData.getFirst().addChild(newNode);
                    parentData.setSecond(parentData.getSecond() - 1);

                    stack.add(parentData);
                    stack.add(new Pair<>(newNode, nChildren));

                    for (int j = stack.size() - 1; j > 0; j--) {
                        Pair<NGramTreeNode, Integer> n = stack.get(j);
                        if (n.getSecond() > 0) {
                            break;
                        }
                        stack.pop();
                    }
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

    void addCorpus(String corpusDir, int nGramLength) throws IOException {
        try {
            for (Path f: Files.walk(Paths.get(corpusDir)).filter(Files::isRegularFile).toList()) {
                String[] tokens = readFile(f.toString()).split(" ");

                for (int i = 0; i < tokens.length - nGramLength + 1; i += nGramLength) {
                    String[] ngram = new String[nGramLength];
                    for (int j = 0; j < nGramLength; j ++) {
                        ngram[j] = tokens[i + j];
                    }
                    addNGram(ngram);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws IOException {
        String serialized = readFile("en.ngrams");
//        System.out.println(serialized);
        NGramTreeNode deserialized = NGramTreeNode.deserialize(serialized);
        System.out.println(deserialized.countChildren());
        System.out.println(Arrays.toString(deserialized.predictNextWord("hi my name is".split(" "))));

//        NGramTreeNode root = new NGramTreeNode("");
//        root.addCorpus("/home/matt/Programming/webscraping/french-listening-bot/src/listensearch/static/data/transcripts/en_ascii/", 6);
//
//        System.out.println(Arrays.toString(root.predictNextWord("hi my name is".split(" "))));
//
//        BufferedWriter writer = new BufferedWriter(new FileWriter("en.ngrams"));
//        writer.write(root.serialize());
//        writer.close();
    }
}
