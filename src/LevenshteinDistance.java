/**
 * The LevenshteinDistance class calculates the Levenshtein distance between two strings.
 * The Levenshtein distance measures the minimum number of single-character edits (insertions,
 * deletions, or substitutions) required to change one string into another.
 */
public class LevenshteinDistance {

    /**
     * Returns the levenshtien distance / edit distance between word1 and word2.
     *
     * @param word1 The first word of the comparison.
     * @param word2 The second word of the comparison.
     * @return The edit distance between word1 and word2.
     */
    public static int calculateDistance(String word1, String word2) {
        int[][] dp = new int[word1.length() + 1][word2.length() + 1];

        for (int i = 0; i <= word1.length(); i++) {
            for (int j = 0; j <= word2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int substitutionCost = word1.charAt(i - 1) == word2.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(dp[i - 1][j - 1] + substitutionCost,
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
                }
            }
        }

        return dp[word1.length()][word2.length()];
    }

    /**
     * Finds the string from the list that has the minimum Levenshtein distance
     * to the expected string.
     *
     * @param expected   The target string to compare against.
     * @param candidates The list of candidate strings to compare.
     * @return The candidate string with the minimum Levenshtein distance.
     */
    public static String findClosestString(String expected, String[] candidates) {
        if (candidates.length == 0) {
            throw new IllegalArgumentException("Candidate list must not be empty.");
        }

        String closestWord = candidates[0];
        int minDistance = calculateDistance(expected, closestWord);

        for (int i = 1; i < candidates.length; i++) {
            String candidate = candidates[i].toLowerCase();
            int distance = calculateDistance(expected, candidate);

            if (distance < minDistance) {
                closestWord = candidate;
                minDistance = distance;
            }
        }

        return closestWord;
    }

    public static void main(String[] args) {
        String word1 = "kitten";
        String word2 = "kitted";

        int distance = calculateDistance(word1, word2);
        System.out.println("Levenshtein distance between \"" + word1 + "\" and \"" + word2 + "\" is: " + distance);
    }
}
