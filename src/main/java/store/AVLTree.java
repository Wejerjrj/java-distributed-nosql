package store;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-balancing binary search tree (AVL Tree) used as a secondary index.
 * Provides O(log N) time complexity for insertions and deletions, and enables
 * efficient lexicographical range queries with pagination.
 */
public class AVLTree {

    /**
     * Represents a single node within the AVL Tree.
     */
    private static class Node {
        String key;
        int height;
        Node left;
        Node right;

        Node(String key) {
            this.key = key;
            this.height = 1;
        }
    }

    private Node root;

    /**
     * Retrieves the height of a given node.
     *
     * @param node The node to evaluate.
     * @return The height of the node, or 0 if the node is null.
     */
    private int height(Node node) {
        return (node == null) ? 0 : node.height;
    }

    /**
     * Calculates the balance factor of a node to determine if rotations are needed.
     *
     * @param node The node to evaluate.
     * @return The balance factor (height of left subtree minus height of right subtree).
     */
    private int getBalance(Node node) {
        return (node == null) ? 0 : height(node.left) - height(node.right);
    }

    /**
     * Performs a right rotation on the given subtree to restore the AVL balance property.
     *
     * @param y The root of the unbalanced subtree.
     * @return The new root of the subtree after rotation.
     */
    private Node rightRotate(Node y) {
        Node x = y.left;
        Node t2 = x.right;

        x.right = y;
        y.left = t2;

        y.height = Math.max(height(y.left), height(y.right)) + 1;
        x.height = Math.max(height(x.left), height(x.right)) + 1;

        return x;
    }

    /**
     * Performs a left rotation on the given subtree to restore the AVL balance property.
     *
     * @param x The root of the unbalanced subtree.
     * @return The new root of the subtree after rotation.
     */
    private Node leftRotate(Node x) {
        Node y = x.right;
        Node t2 = y.left;

        y.left = x;
        x.right = t2;

        x.height = Math.max(height(x.left), height(x.right)) + 1;
        y.height = Math.max(height(y.left), height(y.right)) + 1;

        return y;
    }

    /**
     * Inserts a new key into the AVL Tree and rebalances if necessary.
     *
     * @param key The string key to insert.
     */
    public void insert(String key) {
        root = insertRec(root, key);
    }

    /**
     * Recursive helper method to insert a key and perform balancing rotations.
     *
     * @param node The current node in the traversal.
     * @param key  The key to insert.
     * @return The unchanged node pointer, or a new root if a rotation occurred.
     */
    private Node insertRec(Node node, String key) {
        if (node == null) return new Node(key);

        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = insertRec(node.left, key);
        } else if (cmp > 0) {
            node.right = insertRec(node.right, key);
        } else {
            return node;
        }

        node.height = 1 + Math.max(height(node.left), height(node.right));
        int balance = getBalance(node);

        if (balance > 1 && key.compareTo(node.left.key) < 0) return rightRotate(node);
        if (balance < -1 && key.compareTo(node.right.key) > 0) return leftRotate(node);

        if (balance > 1 && key.compareTo(node.left.key) > 0) {
            node.left = leftRotate(node.left);
            return rightRotate(node);
        }

        if (balance < -1 && key.compareTo(node.right.key) < 0) {
            node.right = rightRotate(node.right);
            return leftRotate(node);
        }

        return node;
    }

    /**
     * Performs a lexicographical range query between start and end keys.
     * Utilizes an early-abort mechanism to prevent unnecessary traversal once the limit is reached.
     *
     * @param start The lower bound of the range (inclusive).
     * @param end   The upper bound of the range (inclusive).
     * @param limit The maximum number of keys to return.
     * @return A list of keys falling within the specified bounds.
     */
    public List<String> range(String start, String end, int limit) {
        List<String> result = new ArrayList<>();
        rangeRec(root, start, end, result, limit);
        return result;
    }

    /**
     * Recursive helper for the range query utilizing in-order traversal.
     *
     * @param node   The current node in the traversal.
     * @param start  The lower bound.
     * @param end    The upper bound.
     * @param result The list accumulating matching keys.
     * @param limit  The pagination limit.
     */
    private void rangeRec(Node node, String start, String end, List<String> result, int limit) {
        if (node == null || result.size() >= limit) return;

        if (node.key.compareTo(start) > 0) {
            rangeRec(node.left, start, end, result, limit);
        }

        if (result.size() < limit && node.key.compareTo(start) >= 0 && node.key.compareTo(end) <= 0) {
            result.add(node.key);
        }

        if (node.key.compareTo(end) < 0) {
            rangeRec(node.right, start, end, result, limit);
        }
    }

    /**
     * Finds the node with the minimum key value in a given subtree.
     *
     * @param node The root of the subtree to search.
     * @return The node containing the smallest key.
     */
    private Node minValueNode(Node node) {
        Node current = node;
        while (current.left != null) {
            current = current.left;
        }
        return current;
    }

    /**
     * Deletes a key from the AVL Tree and rebalances the structure.
     *
     * @param key The key to remove.
     */
    public void delete(String key) {
        root = deleteRec(root, key);
    }

    /**
     * Recursive helper method to delete a key and perform balancing rotations.
     *
     * @param node The current node in the traversal.
     * @param key  The key to delete.
     * @return The updated node pointer, or a new root if a rotation occurred.
     */
    private Node deleteRec(Node node, String key) {
        if (node == null) return null;

        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = deleteRec(node.left, key);
        } else if (cmp > 0) {
            node.right = deleteRec(node.right, key);
        } else {
            if ((node.left == null) || (node.right == null)) {
                Node temp = (node.left != null) ? node.left : node.right;
                if (temp == null) {
                    temp = node;
                    node = null;
                } else {
                    node = temp;
                }
            } else {
                Node temp = minValueNode(node.right);
                node.key = temp.key;
                node.right = deleteRec(node.right, temp.key);
            }
        }

        if (node == null) return null;

        node.height = Math.max(height(node.left), height(node.right)) + 1;
        int balance = getBalance(node);

        if (balance > 1 && getBalance(node.left) >= 0) return rightRotate(node);

        if (balance > 1 && getBalance(node.left) < 0) {
            node.left = leftRotate(node.left);
            return rightRotate(node);
        }

        if (balance < -1 && getBalance(node.right) <= 0) return leftRotate(node);

        if (balance < -1 && getBalance(node.right) > 0) {
            node.right = rightRotate(node.right);
            return leftRotate(node);
        }

        return node;
    }

    /**
     * Clears the entire tree by removing the root reference,
     * allowing the garbage collector to reclaim the memory.
     */
    public void clear() {
        root = null;
    }
}