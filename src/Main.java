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
}