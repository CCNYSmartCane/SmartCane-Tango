package com.projecttango.examples.java.helloareadescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

class PathFinder {
    static int xLength;
    static int yLength;
    static boolean[][] coordinateMatrix;
    private static List<Node> totalPath;
    static List<Node> squashedPath;
    static float granularity;

    static boolean pathfind(Node start, Node goal) {
        List<Node> closedList = new ArrayList<Node>();
        Queue<Node> openList = new PriorityQueue<Node>(11, new ComparatorfScore());

        start.setgScore(0);
        start.setfScore(distanceBetween(start, goal));
        openList.add(start);

        while (!openList.isEmpty()) {
            // Get the node that has the lowest fScore
            Node current = openList.remove();

            if (current.equals(goal)) {
                totalPath = reconstructPath(current);
                if(totalPath.size() > 2) {
                    squashedPath = DouglasPeucker(totalPath, 1.5/granularity); // 1.5 meter threshold
                } else {
                    squashedPath = totalPath;
                }
                return true;
            }

            closedList.add(current);
            System.out.println(closedList.size());

            List<Node> neighbors = getNeighbors(current);
            for (Node n : neighbors) {
                if (closedList.contains(n)) {
                    continue;
                }

                int tentativegScore = current.getgScore() + distanceBetween(current, n);
                if (!openList.contains(n)) {
                    openList.add(n);
                } else if (tentativegScore >= n.getgScore()) {
                    // Already in openList but the gScore is higher than
                    // originally
                    continue;
                }

                n.setPrev(current);
                n.setgScore(tentativegScore);
                n.setfScore(n.getgScore() + distanceBetween(n, goal));
            }
        }

        return false;
    }

    private static int distanceBetween(Node current, Node n) {
        return Math.abs(current.getX() - n.getX()) + Math.abs(current.getY() - n.getY());
    }

    private static List<Node> reconstructPath(Node goal) {
        Node cursor = goal;
        List<Node> totalPath = new ArrayList<Node>();

        while (cursor != null) {
            totalPath.add(cursor);
            cursor = cursor.getPrev();
        }

        Collections.reverse(totalPath);

        return totalPath;
    }

    private static List<Node> getNeighbors(Node current) {
        List<Node> neighbors = new ArrayList<Node>();
        int x = current.getX();
        int y = current.getY();
        int neighborX;
        int neighborY;

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }

                neighborX = x + i;
                neighborY = y + j;
                if ((neighborX >= 0 && neighborX < xLength) && (neighborY >= 0 && neighborY < yLength)
                        && coordinateMatrix[neighborX][neighborY]) {
                    neighbors.add(new Node(neighborX, neighborY));
                }
            }
        }

        return neighbors;
    }

    private static List<Node> squashPath(List<Node> path) {
        List<Node> squashedPath = new ArrayList<Node>();
        Node current;
        Node next;
        int xDiff = 0;
        int yDiff = 0;

        for (int i = 0; i < path.size(); i++) {
            current = path.get(i);
            squashedPath.add(current);

            if (i + 1 != path.size()) {
                next = path.get(i + 1);
                xDiff = next.getX() - current.getX();
                yDiff = next.getY() - current.getY();
                current = next;
            }
            for (int j = i + 2; j < path.size(); j++) {
                next = path.get(j);
                if (((next.getX() - current.getX()) != xDiff) || ((next.getY() - current.getY()) != yDiff)) {
                    // end of straight path
                    i = j - 2;
                    break;
                } else {
                    // continue straight path
                    current = next;
                }
                // finishes checking path list, so ensure the last node is
                // inserted
                i = j - 1;
            }
        }
        return squashedPath;
    }

    private static List<Node> DouglasPeucker(List<Node> path, double epsilon) {
        // Find the point that is max distance from line
        double dmax = 0;
        int index = 0;
        int last = path.size() - 1;

        for (int i = 1; i < last; i++) {
            double d = perpendicularDistance(path.get(i), path.get(0), path.get(last));
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }
        // If dmax is greater than the threshold, recursively call both sides
        List<Node> results = new ArrayList<Node>();
        if (dmax > epsilon) {
            List<Node> results1 = DouglasPeucker(path.subList(0, index), epsilon);
            List<Node> results2 = DouglasPeucker(path.subList(index, last), epsilon);
            results.addAll(results1);
            // Remove the middle otherwise there would be duplicate instances
            results.remove(results.size() - 1);
            results.addAll(results2);
        } else {
            results.add(path.get(0));
            results.add(path.get(last));
        }
        return results;
    }

    private static double perpendicularDistance(Node p, Node lineStart, Node lineEnd) {
        int y2 = lineEnd.getY();
        int y1 = lineStart.getY();
        int x2 = lineEnd.getX();
        int x1 = lineStart.getX();

        return Math.abs((y2 - y1) * p.getX() - (x2 - x1) * p.getY() + x2 * y1 - y2 * x1)
                / euclideanDistance(lineStart, lineEnd);
    }

    private static double euclideanDistance(Node current, Node n) {
        return Math.sqrt(Math.pow(current.getX() - n.getX(), 2) + Math.pow(current.getY() - n.getY(), 2));
    }
}

