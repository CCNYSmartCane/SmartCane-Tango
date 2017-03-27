package com.projecttango.examples.java.helloareadescription;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by ChrisYang on 3/1/17.
 */

class Node {
    private float x;
    private float y;
    private Node prev;
    private float gScore;
    private float fScore;

    @JsonCreator
    public Node(@JsonProperty("x")float x, @JsonProperty("y")float y) {
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

        if (Float.compare(node.x, x) != 0)
            return false;
        return Float.compare(node.y, y) == 0;

    }

    @Override
    public int hashCode() {
        int result = (x != +0.0f ? Float.floatToIntBits(x) : 0);
        result = 31 * result + (y != +0.0f ? Float.floatToIntBits(y) : 0);
        return result;
    }
}
