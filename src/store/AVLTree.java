package store;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-balancing AVL Tree implementation optimized for lexicographical range queries.
 */
public class AVLTree {

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

    private int height(Node node) {
        return (node == null) ? 0 : node.height;
    }

    private int getBalance(Node node) {
        return (node == null) ? 0 : height(node.left) - height(node.right);
    }

    private Node rightRotate(Node y) {
        Node x = y.left;
        Node t2 = x.right;

        x.right = y;
        y.left = t2;

        y.height = Math.max(height(y.left), height(y.right)) + 1;
        x.height = Math.max(height(x.left), height(x.right)) + 1;

        return x;
    }

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
     * Inserts a key into the AVL tree, performing necessary rotations to maintain balance.
     *
     * @param key The string key to insert.
     */
    public void insert(String key) {
        root = insertRec(root, key);
    }

    private Node insertRec(Node node, String key) {
        if (node == null) {
            return new Node(key);
        }

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

        if (balance > 1 && key.compareTo(node.left.key) < 0) {
            return rightRotate(node);
        }
        if (balance < -1 && key.compareTo(node.right.key) > 0) {
            return leftRotate(node);
        }
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
     * Retrieves a list of keys that fall within the specified lexicographical range.
     *
     * @param start The inclusive starting key.
     * @param end   The inclusive ending key.
     * @return A list of keys within the given range in alphabetical order.
     */
    public List<String> range(String start, String end) {
        List<String> result = new ArrayList<>();
        rangeRec(root, start, end, result);
        return result;
    }

    private void rangeRec(Node node, String start, String end, List<String> result) {
        if (node == null) {
            return;
        }

        if (node.key.compareTo(start) > 0) {
            rangeRec(node.left, start, end, result);
        }
        if (node.key.compareTo(start) >= 0 && node.key.compareTo(end) <= 0) {
            result.add(node.key);
        }
        if (node.key.compareTo(end) < 0) {
            rangeRec(node.right, start, end, result);
        }
    }

    private Node minValueNode(Node node) {
        Node current = node;
        while (current.left != null) {
            current = current.left;
        }
        return current;
    }

    /**
     * Deletes a key from the AVL tree and rebalances the structure.
     *
     * @param key The key to remove.
     */
    public void delete(String key) {
        root = deleteRec(root, key);
    }

    private Node deleteRec(Node node, String key) {
        if (node == null) {
            return null;
        }

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

        if (node == null) {
            return null;
        }

        node.height = Math.max(height(node.left), height(node.right)) + 1;
        int balance = getBalance(node);

        if (balance > 1 && getBalance(node.left) >= 0) {
            return rightRotate(node);
        }
        if (balance > 1 && getBalance(node.left) < 0) {
            node.left = leftRotate(node.left);
            return rightRotate(node);
        }
        if (balance < -1 && getBalance(node.right) <= 0) {
            return leftRotate(node);
        }
        if (balance < -1 && getBalance(node.right) > 0) {
            node.right = rightRotate(node.right);
            return leftRotate(node);
        }

        return node;
    }

    /**
     * Clears all entries from the AVL tree.
     */
    public void clear() {
        root = null;
    }
}