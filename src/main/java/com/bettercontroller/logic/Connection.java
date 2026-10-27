package com.bettercontroller.logic;

/** A wire from an output port of one node to an input port of another node. */
public record Connection(int fromNode, int fromPort, int toNode, int toPort) {
}
