public class Main {
    public static void main(String[] args) throws DeserializationException {
        while (true) {
            NGramTreeNode randomTree = RandomTreeGenerator.generateRandomTree(10, 6, 10);
            NGramTreeNode deserializedRandomTree = NGramTreeNode.deserialize(randomTree.serialize());
            System.out.println(randomTree.countChildren());

            if (!deserializedRandomTree.deepEquals(randomTree)) {
//                System.out.println(randomTree.serialize());
//                System.out.println(deserializedRandomTree.serialize());

                break;
            }
        }
    }
}