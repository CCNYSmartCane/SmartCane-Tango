package com.projecttango.examples.java.helloareadescription;

/**
 * Created by ChrisYang on 3/1/17.
 */

class Node {
    private float x;
    private float y;
    private Node prev;
    private float gScore;
    private float fScore;

    public Node(float x, float y) {
        this.x = x;
        this.y = y;
        this.prev = null;
        this.gScore = Float.MAX_VALUE;
        this.fScore = Float.MAX_VALUE;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public Node getPrev() {
        return prev;
    }

    public void setPrev(Node prev) {
        this.prev = prev;
    }

    public float getgScore() {
        return gScore;
    }

    public void setgScore(float gScore) {
        this.gScore = gScore;
    }

    public float getfScore() {
        return fScore;
    }

    public void setfScore(float fScore) {
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
