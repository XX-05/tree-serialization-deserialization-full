/*
File: NGramTreeNode.java
Copyright (c) August 18, 2023, Matthew Aharonian

Author: Matthew Aharonian
Created: August 18, 2023
Version: 1.0

Description:
NGramTreeNode is a class representing a node in an N-gram tree. This class
is used for creating and managing N-gram trees, which are commonly employed
in natural language processing tasks such as text prediction and language
modeling. Each node in the tree holds a word and references to its child nodes,
enabling the representation of word sequences and their relationships.

This class offers a range of methods for working with N-gram nodes,
including adding child nodes, searching for predictions, and comparing nodes
for equality.

Disclaimer:
This code is provided as-is and is not guaranteed to be error-free. It is
intended for educational and reference purposes.
*/
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;


/**
 * This class represents a node in an N-gram tree, where each node contains a word and references to its child nodes.
 * N-gram trees are commonly used in natural language processing for predictive text and language modeling.
 */
public class NGramTreeNode {
    private final String word;
    private final HashMap<String, NGramTreeNode> children;

    /**
     * Constructs a new NGramTreeNode with the specified word.
     *
     * @param nodeWord The word associated with this node.
     */
    public NGramTreeNode(String nodeWord) {
        word = nodeWord;
        children = new HashMap<>();
    }

    /**
     * Returns the word associated with this node.
     *
     * @return The word associated with this node.
     */
    public String getWord() {
        return word;
    }

    /**
     * Returns a string representation of this node and its children
     * in the format: <LetterTreeNode: word; Children: <>>.
     *
     * @return A string representation of this node and its children.
     */
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

    /**
     * Does a deep comparison between this node and another.
     *
     * @param otherNode The other NGramTreeNode to compare.
     * @return true if the nodes are deep equal, false otherwise.
     */
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

    /**
     * Returns an array of the child nodes attached to this node.
     *
     * @return An array of child nodes.
     */
    public NGramTreeNode[] getChildren() {
        return children.values().toArray(new NGramTreeNode[0]);
    }

    public ArrayList<String> getChildWords() {
        ArrayList<String> childWords = new ArrayList<>();

        for (NGramTreeNode c : children.values()) {
            childWords.add(c.word);
        }

        return childWords;
    }

    /**
     * Returns the number of children directly attached to this node.
     *
     * @return The number of children directly attached to this node.
     */
    public int getChildrenCount() {
        return children.size();
    }

    /**
     * Adds a child node to this node.
     *
     * @param childNode The child node to add.
     */
    public void addChild(NGramTreeNode childNode) {
        children.put(childNode.word, childNode);
    }

    /**
     * Adds a word as a child node to this node then returns the new node.
     * If the word already exists as a child, returns the existing child.
     *
     * @param word The word to add as a child node.
     * @return The child node corresponding to the added word.
     */
    public NGramTreeNode addWord(String word) {
        if (children.containsKey(word))
            return children.get(word);

        NGramTreeNode newNode = new NGramTreeNode(word.toLowerCase());
        addChild(newNode);
        return newNode;
    }

    /**
     * Creates a branch stemming from this node
     * with each child node being a word from the provided n-gram.
     *
     * E.g., the n-gram "the quick brown fox" would create the branch:
     *  the -> quick -> brown -> fox
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
