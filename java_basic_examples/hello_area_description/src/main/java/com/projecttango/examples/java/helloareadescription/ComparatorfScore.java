package com.projecttango.examples.java.helloareadescription;

import java.util.Comparator;

public class ComparatorfScore implements Comparator<Node> {
    ComparatorfScore() {

    }

    @Override
    public int compare(Node n1, Node n2) {
        if (n1.getfScore() == n2.getfScore()) {
            return 0;
        }

        return n1.getfScore() > n2.getfScore() ? 1 : -1;
    }

}