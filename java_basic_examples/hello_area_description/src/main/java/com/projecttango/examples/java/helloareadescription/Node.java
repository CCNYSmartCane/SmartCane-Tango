package com.projecttango.examples.java.helloareadescription;

/**
 * Created by ChrisYang on 3/1/17.
 */

class Node {
    private int x;
    private int y;

    public Node(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Node node = (Node) o;

        return this.x==node.x && this.y==node.y;
    }
}
