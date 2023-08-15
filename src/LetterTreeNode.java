import java.util.Map;
import java.util.Stack;
import java.util.HashMap;
import java.util.ArrayList;

class DeserializationException extends Exception {
    public DeserializationException(String message) {
        super(message);
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


public class LetterTreeNode {
    private final String letter;
    private final HashMap<String, LetterTreeNode> children;

    public LetterTreeNode(String nodeLetter) {
        letter = nodeLetter;
        children = new HashMap<>();
    }

    public String toString() {
        StringBuilder childrenString = new StringBuilder();
        if (children.size() > 0) {
            for (LetterTreeNode child : children.values()) {
                childrenString.append(child.toString()).append(", ");
            }
            childrenString.setLength(childrenString.length() - 2);
            return String.format("<LetterTreeNode: %s; Children: %s>", letter, childrenString);
        } else {
            return String.format("<LetterTreeNode: %s>", letter);
        }
    }

    public boolean deepEquals(LetterTreeNode otherNode) {
        if (!this.letter.equals(otherNode.letter)) {
            return false;
        }

        if (this.children.size() != otherNode.children.size()) {
            return false;
        }

        for (Map.Entry<String, LetterTreeNode> entry : this.children.entrySet()) {
            String key = entry.getKey();
            LetterTreeNode thisChild = entry.getValue();
            LetterTreeNode otherChild = otherNode.children.get(key);

            if (otherChild == null || !thisChild.deepEquals(otherChild)) {
                return false;
            }
        }

        return true;
    }

    public int countChildren() {
        Stack<LetterTreeNode> stack = new Stack<>();
        stack.add(this);

        int nodesSeen = 0;
        while (!stack.isEmpty()) {
            LetterTreeNode node = stack.pop();
            stack.addAll(node.children.values());
            nodesSeen ++;
        }

        return nodesSeen;
    }

    public LetterTreeNode[] getChildren() {
        return children.values().toArray(new LetterTreeNode[0]);
    }

    public int getChildrenCount() {
        return children.size();
    }

    public void addChild(LetterTreeNode childNode) {
        children.put(childNode.letter, childNode);
    }

    public LetterTreeNode addLetter(String letter) {
        LetterTreeNode newNode = new LetterTreeNode(letter.toUpperCase());
        addChild(newNode);
        return newNode;
    }

    public String serialize() {
        StringBuilder flattened = new StringBuilder();
        Stack<LetterTreeNode> stack = new Stack<>();

        stack.add(this);

        while (!stack.isEmpty()) {
            LetterTreeNode node = stack.pop();
            String nodeInfo = node.letter + "|" + node.children.size() + "]";
            flattened.append(nodeInfo);
            stack.addAll(node.children.values());
        }

        return flattened.toString();
    }

    static ArrayList<Pair<LetterTreeNode, Integer>> deserializeFlattened(String serializedData) {
        ArrayList<Pair<LetterTreeNode, Integer>> flattened = new ArrayList<>();

        StringBuilder buff = new StringBuilder();
        String letter = "";

        for (int i = 0; i < serializedData.length(); i ++) {
            String currChar = serializedData.substring(i, i + 1);

            switch (currChar) {
                case "]":
                    LetterTreeNode newNode = new LetterTreeNode(letter);
                    int nChildren = Integer.parseInt(buff.toString());

                    flattened.add(new Pair<>(newNode, nChildren));

                    buff.setLength(0);
                    letter = "";
                    break;
                case "|":
                    letter = buff.toString();
                    buff.setLength(0);
                    break;
                default:
                    buff.append(currChar);
            }
        }

        return flattened;
    }

    public static LetterTreeNode deserialize(String serializedData) throws DeserializationException {
        Stack<Pair<LetterTreeNode, Integer>> stack = new Stack<>();
        ArrayList<Pair<LetterTreeNode, Integer>> flattened = deserializeFlattened(serializedData);

        if (flattened.isEmpty()) {
            throw new DeserializationException("No data or improperly formatted data!");
        }

        LetterTreeNode rootNode = flattened.get(0).getFirst();
        stack.add(flattened.get(0));
        flattened.remove(0);

        for (Pair<LetterTreeNode, Integer> nodeData : flattened) {
            Pair<LetterTreeNode, Integer> parentData = stack.pop();
            parentData.getFirst().addChild(nodeData.getFirst());
            parentData.setSecond(parentData.getSecond() - 1);

            stack.add(parentData);
            stack.add(nodeData);

            for (int i = stack.size() - 1; i > 0; i --) {
                Pair<LetterTreeNode, Integer> n = stack.get(i);
                if (n.getSecond() > 0) {
                    break;
                }
                stack.pop();
            }
        }

        return rootNode;
    }
}
