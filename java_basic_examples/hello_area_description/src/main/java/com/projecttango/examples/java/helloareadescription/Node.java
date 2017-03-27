package com.projecttango.examples.java.helloareadescription;

class Node {
    private int x;
    private int y;
    private Node prev;
    private int gScore;
    private int fScore;

    Node(int x, int y) {
        this.x = x;
        this.y = y;
        this.prev = null;
        this.gScore = Integer.MAX_VALUE;
        this.fScore = Integer.MAX_VALUE;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    Node getPrev() {
        return prev;
    }

    void setPrev(Node prev) {
        this.prev = prev;
    }

    int getgScore() {
        return gScore;
    }

    void setgScore(int gScore) {
        this.gScore = gScore;
    }

    int getfScore() {
        return fScore;
    }

    void setfScore(int fScore) {
        this.fScore = fScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Node node = (Node) o;

        return this.x == node.x && this.y == node.y;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return result;
    }
}
