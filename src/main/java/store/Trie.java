package store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Trie (Prefix Tree) data structure used as a secondary index.
 * Enables highly efficient O(K) prefix-based auto-completion searches.
 */
public class Trie {

    /**
     * Represents a single character node within the Trie.
     */
    private static class TrieNode {
        // HashMap minimizes memory overhead compared to a TreeMap
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEndOfKey = false;
    }

    private final TrieNode root;

    /**
     * Constructs an empty Trie.
     */
    public Trie() {
        root = new TrieNode();
    }

    /**
     * Inserts a string key into the Trie.
     *
     * @param key The string to insert.
     */
    public void insert(String key) {
        TrieNode current = root;
        for (char ch : key.toCharArray()) {
            current.children.putIfAbsent(ch, new TrieNode());
            current = current.children.get(ch);
        }
        current.isEndOfKey = true;
    }

    /**
     * Removes a string key from the Trie.
     * Cleans up orphaned character nodes automatically to preserve memory.
     *
     * @param key The string to remove.
     */
    public void remove(String key) {
        removeRecursive(root, key, 0);
    }

    /**
     * Recursive helper method to delete a key and prune unused child nodes.
     *
     * @param current The current node in traversal.
     * @param key     The key being removed.
     * @param index   The current character index.
     * @return True if the current node can be safely deleted by its parent, false otherwise.
     */
    private boolean removeRecursive(TrieNode current, String key, int index) {
        if (index == key.length()) {
            if (!current.isEndOfKey) return false;
            current.isEndOfKey = false;
            return current.children.isEmpty();
        }

        char ch = key.charAt(index);
        TrieNode node = current.children.get(ch);
        if (node == null) return false;

        boolean shouldDeleteChild = removeRecursive(node, key, index + 1);
        if (shouldDeleteChild) {
            current.children.remove(ch);
            return current.children.isEmpty() && !current.isEndOfKey;
        }
        return false;
    }

    /**
     * Performs a paginated prefix search.
     *
     * @param prefix The string prefix to match against.
     * @param limit  The maximum number of keys to return.
     * @return A list of keys starting with the specified prefix.
     */
    public List<String> getKeysWithPrefix(String prefix, int limit) {
        List<String> results = new ArrayList<>();
        TrieNode current = root;

        for (char ch : prefix.toCharArray()) {
            TrieNode node = current.children.get(ch);
            if (node == null) return results;
            current = node;
        }

        dfs(current, new StringBuilder(prefix), results, limit);
        return results;
    }

    /**
     * Depth-First Search helper to traverse and collect matching keys.
     * Uses early-abort pagination to prevent OutOfMemory errors on massive datasets.
     *
     * @param node        The current node in traversal.
     * @param currentPath The accumulated character path forming the string.
     * @param results     The list accumulating matching keys.
     * @param limit       The pagination limit.
     */
    private void dfs(TrieNode node, StringBuilder currentPath, List<String> results, int limit) {
        if (results.size() >= limit) return;

        if (node.isEndOfKey) {
            results.add(currentPath.toString());
        }

        // Sort keys on-the-fly to guarantee lexicographical order
        List<Character> keys = new ArrayList<>(node.children.keySet());
        Collections.sort(keys);

        for (char ch : keys) {
            if (results.size() >= limit) return;

            currentPath.append(ch);
            dfs(node.children.get(ch), currentPath, results, limit);
            currentPath.deleteCharAt(currentPath.length() - 1); // Backtrack
        }
    }

    /**
     * Clears the entire Trie structure by dropping all root children references.
     */
    public void clear() {
        root.children.clear();
    }
}