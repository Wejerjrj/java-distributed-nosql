package store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A prefix tree (Trie) implementation for efficient string prefix matching.
 * Utilizes a TreeMap for children to implicitly maintain lexicographical ordering of keys.
 */
public class Trie {

    private static class TrieNode {
        Map<Character, TrieNode> children = new TreeMap<>();
        boolean isEndOfKey = false;
    }

    private final TrieNode root;

    /**
     * Constructs a new empty Trie.
     */
    public Trie() {
        root = new TrieNode();
    }

    /**
     * Inserts a key into the Trie.
     *
     * @param key The string key to insert.
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
     * Removes a key from the Trie and prunes any dead branches.
     *
     * @param key The string key to remove.
     */
    public void remove(String key) {
        removeRecursive(root, key, 0);
    }

    private boolean removeRecursive(TrieNode current, String key, int index) {
        if (index == key.length()) {
            if (!current.isEndOfKey) {
                return false;
            }
            current.isEndOfKey = false;
            return current.children.isEmpty();
        }

        char ch = key.charAt(index);
        TrieNode node = current.children.get(ch);
        if (node == null) {
            return false;
        }

        boolean shouldDeleteChild = removeRecursive(node, key, index + 1);

        if (shouldDeleteChild) {
            current.children.remove(ch);
            return current.children.isEmpty() && !current.isEndOfKey;
        }

        return false;
    }

    /**
     * Retrieves all keys starting with the specified prefix.
     *
     * @param prefix The prefix to match.
     * @return A list of matching keys in lexicographical order.
     */
    public List<String> getKeysWithPrefix(String prefix) {
        List<String> results = new ArrayList<>();
        TrieNode current = root;

        for (char ch : prefix.toCharArray()) {
            TrieNode node = current.children.get(ch);
            if (node == null) {
                return results;
            }
            current = node;
        }

        dfs(current, new StringBuilder(prefix), results);
        return results;
    }

    private void dfs(TrieNode node, StringBuilder currentPath, List<String> results) {
        if (node.isEndOfKey) {
            results.add(currentPath.toString());
        }
        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            currentPath.append(entry.getKey());
            dfs(entry.getValue(), currentPath, results);
            currentPath.deleteCharAt(currentPath.length() - 1);
        }
    }

    /**
     * Clears all entries from the Trie.
     */
    public void clear() {
        root.children.clear();
    }
}