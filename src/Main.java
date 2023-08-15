public class Main {
    public static void main(String[] args) throws DeserializationException {
        while (true) {
            LetterTreeNode randomTree = RandomTreeGenerator.generateRandomTree(10, 5, 10);
            LetterTreeNode deserializedRandomTree = LetterTreeNode.deserialize(randomTree.serialize());
            System.out.println(randomTree.countChildren());

            if (!deserializedRandomTree.deepEquals(randomTree)) {
                System.out.println(randomTree.serialize());
                System.out.println(deserializedRandomTree.serialize());
                break;
            }
        }
    }
}