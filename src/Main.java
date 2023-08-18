import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    public static String readFile(String fileName) throws IOException {
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
    public static void main(String[] args){
        while (true) {
            NGramTreeNode randomTree = RandomTreeGenerator.generateRandomTree(10, 6, 10);
            NGramTreeNode deserializedRandomTree = NGramTreeNode.deserialize(randomTree.serialize());
            System.out.println(randomTree.branchSize());

            if (!deserializedRandomTree.deepEquals(randomTree)) {
//                System.out.println(randomTree.serialize());
//                System.out.println(deserializedRandomTree.serialize());

                break;
            }
        }
    }
}