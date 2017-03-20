package com.projecttango.examples.java.helloareadescription;

/**
 * Created by ChrisYang on 3/1/17.
 */

class Node {
    private int x;
    private int y;
    private Node prev;
    private int gScore;
    private int fScore;

    public Node(int x, int y) {
        this.x = x;
        this.y = y;
        this.prev = null;
        this.gScore = Integer.MAX_VALUE;
        this.fScore = Integer.MAX_VALUE;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Node getPrev() {
        return prev;
    }

    public void setPrev(Node prev) {
        this.prev = prev;
    }

    public int getgScore() {
        return gScore;
    }

    public void setgScore(int gScore) {
        this.gScore = gScore;
    }

    public int getfScore() {
        return fScore;
    }

    public void setfScore(int fScore) {
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
    public String toString(){
        return String.valueOf(x) + ", " + String.valueOf(y);
    }
}
