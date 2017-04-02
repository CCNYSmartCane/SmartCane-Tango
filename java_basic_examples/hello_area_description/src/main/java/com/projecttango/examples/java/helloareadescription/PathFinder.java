package com.projecttango.examples.java.helloareadescription;

/**
 * Created by ChrisYang on 3/2/17.
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

class PathFinder {
    static Set<Node> coordinateSet;
    static List<Node> totalPath;
    static List<Node> squashedPath;
    static float granularity;

    public static boolean pathfind(Node start, Node goal) {
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
                    squashedPath = DouglasPeucker(totalPath, 0.75);
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

                float tentativegScore = current.getgScore() + distanceBetween(current, n);
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

    private static List<Node> getNeighbors(Node current) {
        List<Node> neighbors = new ArrayList<Node>();
        float x = current.getX();
        float y = current.getY();
        float neighborX;
        float neighborY;

        for (int i = -1; i < 2; i++) {
            for (int j = -1; j < 2; j++) {
                if (i == 0 && j == 0) {
                    continue;
                }

                neighborX = x + i*granularity;
                neighborY = y + j*granularity;
                Node neighbor = new Node(neighborX, neighborY);
                if (coordinateSet.contains(neighbor)) {
                    neighbors.add(neighbor);
                }

            }
        }

        return neighbors;
    }

    private static float distanceBetween(Node current, Node n) {
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
            List<Node> results1 = DouglasPeucker(path.subList(0, index+1), epsilon);
            List<Node> results2 = DouglasPeucker(path.subList(index, last+1), epsilon);
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
        float y2 = lineEnd.getY();
        float y1 = lineStart.getY();
        float x2 = lineEnd.getX();
        float x1 = lineStart.getX();

        return Math.abs((y2 - y1) * p.getX() - (x2 - x1) * p.getY() + x2 * y1 - y2 * x1)
                / euclideanDistance(lineStart, lineEnd);
    }

    private static double euclideanDistance(Node current, Node n) {
        return Math.sqrt(Math.pow(current.getX() - n.getX(), 2) + Math.pow(current.getY() - n.getY(), 2));
    }
}
