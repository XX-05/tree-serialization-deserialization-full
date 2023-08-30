import java.util.HashMap;
import java.util.Map;
import java.util.Stack;


class MalformedSerialBinaryException extends Exception {
    public MalformedSerialBinaryException(String msg) {
        super(msg);
    }
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
}
